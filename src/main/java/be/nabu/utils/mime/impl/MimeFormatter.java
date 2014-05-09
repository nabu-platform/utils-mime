package be.nabu.utils.mime.impl;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import be.nabu.libs.resources.api.Resource;
import be.nabu.utils.io.IOUtils;
import static be.nabu.utils.io.IOUtils.*;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.api.WritableContainer;
import be.nabu.utils.mime.api.ContentPart;
import be.nabu.utils.mime.api.ContentTransferTranscoder;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.api.MultiPart;
import be.nabu.utils.mime.api.Part;
import be.nabu.utils.mime.api.PartFormatter;
import be.nabu.utils.mime.util.ChunkedWritableByteContainer;

public class MimeFormatter implements PartFormatter {

	private String mimeVersion = "1.0";
	private ContentTransferTranscoder transcoder;
	
	/**
	 * For mails, binary data is not allowed so we need to encode it
	 * However for http binary is not a problem, set this boolean to true to allow binary streams
	 */
	private boolean allowBinary = false;
	
	/**
	 * The default chunk size is 200kb
	 * If it is too small, the download may actually be too slow
	 */
	private int chunkSize = 1024 * 200;
	
	private List<String> quotableContentTypes = new ArrayList<String>(); {
		quotableContentTypes.add("text/plain");
		quotableContentTypes.add("text/xml");
		quotableContentTypes.add("text/html");
		quotableContentTypes.add("application/xml");
	}
	
	private List<String> unencodedContentTypes = new ArrayList<String>(); {
		unencodedContentTypes.add("application/x-www-form-urlencoded");
	}
	
	@Override
	public void format(Part part, WritableContainer<ByteBuffer> output) throws IOException, FormatException {
		if (part instanceof FormattablePart) {
			((FormattablePart) part).setFormatter(this);
			((FormattablePart) part).format(output);
		}
		else if (isMultiPart(part))
			formatMultiPart((MultiPart) part, output);
		else if (part instanceof ContentPart)
			formatContentPart((ContentPart) part, output);
		else
			throw new FormatException("Could not format part of type " + part.getClass().getName() + ", it is not a content part and not a multipart");
	}
	
	private boolean isMultiPart(Part part) {
		String contentType = MimeUtils.getContentType(part.getHeaders()).toLowerCase();
		return contentType.startsWith("multipart/") || contentType.equals(Resource.CONTENT_TYPE_DIRECTORY);
	}
	
	private String getContentTransferEncoding(Part part) {
		String contentType = MimeUtils.getContentType(part.getHeaders()).toLowerCase();
		if (unencodedContentTypes.contains(contentType))
			return null;
		else if (quotableContentTypes.contains(contentType))
			return "quoted-printable";
		else
			return "base64";
	}

	public List<String> getQuotableContentTypes() {
		return quotableContentTypes;
	}
	
	public List<String> getUnencodedContentTypes() {
		return unencodedContentTypes;
	}
	
	public static void writeHeaders(WritableContainer<ByteBuffer> output, Header...headers) throws IOException {
		try {
			for (Header header : headers) {
				// the mime header does proper formatting
				if (!(header instanceof MimeHeader))
					header = new MimeHeader(header.getName(), header.getValue(), header.getComments());
				output.write(wrap((header.toString() + "\r\n").getBytes("ASCII"), true));
			}
		}
		catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static void finishHeaders(WritableContainer<ByteBuffer> output) throws IOException {
		try {
			output.write(wrap("\r\n".getBytes("ASCII"), true));
		}
		catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
	
	private void formatContentPart(ContentPart part, WritableContainer<ByteBuffer> output) throws IOException {
		String contentTransferEncoding = MimeUtils.getContentTransferEncoding(part.getHeaders());
		String transferEncoding = MimeUtils.getTransferEncoding(part.getHeaders());
		String contentEncoding = MimeUtils.getContentEncoding(part.getHeaders());
		
		writeHeaders(output, part.getHeaders());
		// make an educated guess
		if (!allowBinary && contentTransferEncoding == null) {
			contentTransferEncoding = getContentTransferEncoding(part);
			if (contentTransferEncoding != null)
				writeHeaders(output, new MimeHeader("Content-Transfer-Encoding", contentTransferEncoding));
		}
		finishHeaders(output);
		
		ReadableContainer<ByteBuffer> content = part.getReadable();
		try {
			String contentRange = MimeUtils.getContentRange(part.getHeaders());
			if (contentRange != null) {
				// format: from-to/total; from & to are inclusive!
				int indexHyphen = contentRange.indexOf("-");
				int indexSlash = contentRange.indexOf("/");
				if (indexHyphen == -1 || indexSlash == -1)
					throw new IllegalArgumentException("The content-range header is misformed, it should be off the format 'from-to/total'");
				long from = new Long(contentRange.substring(0, indexHyphen));
				long to = new Long(contentRange.substring(indexHyphen + 1, indexSlash));
				content.read(newByteSink(from));
				// @REMOVE
//				IOUtils.skip(content, from);
				// the to is inclusive!
				content = IOUtils.limitReadable(content, to + 1);
			}
			
			// you can do two things here:
			// encode the input as you are reading, however this presumes the input returns a clean -1 which would trigger the "flush" in the transcoder
			// or you can encode the output as you are writing so you can manually flush
			// the second option is of course preferable
			// first apply actual transfer encoding (if necessary). This is mostly for chunking but can also be gzip etc
			// to make matters slightly muddier, the values for content encoding are +- the same as for transfer encoding (they are both http constructs)
			// the values for contentTransferEncoding are different as they are aimed at mime
			WritableContainer<ByteBuffer> encodedOutput = getTranscoder().encodeContent(transferEncoding, output);
			if (encodedOutput instanceof ChunkedWritableByteContainer)
				encodedOutput = bufferWritable(encodedOutput, newByteBuffer(chunkSize, true));
			// then apply content transfer encoding, it allows for base64 etc, it is usually not combined with transfer-encoding in the above
			encodedOutput = getTranscoder().encodeTransfer(contentTransferEncoding, encodedOutput);
			// last but not least: content-encoding. this is end-to-end instead of hop-to-hop
			// in other words, a transfer-encoding gzip can be unzipped by an intermediate server while a content-encoding gzip should be unzipped by the client
			encodedOutput = getTranscoder().encodeContent(contentEncoding, encodedOutput);
			copyBytes(content, encodedOutput);
			encodedOutput.flush();
			output.write(wrap("\r\n\r\n".getBytes("ASCII"), true));
		}
		catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		finally {
			content.close();
		}
	}
	
	private void formatMultiPart(MultiPart part, WritableContainer<ByteBuffer> output) throws IOException, FormatException {
		Header originalContentType = MimeUtils.getHeader("Content-Type", part.getHeaders());
		MimeHeader contentType = null;
		if (originalContentType == null)
			contentType = new MimeHeader("Content-Type", "multipart/mixed");
		else
			contentType = new MimeHeader(originalContentType.getName(), originalContentType.getValue(), originalContentType.getComments());
		String boundary = MimeUtils.getBoundary(contentType);
		if (boundary == null) {
			boundary = "-=part." + MimeUtils.generateBoundary();
			contentType.addComment("boundary=" + boundary);
		}
		writeHeaders(output, new MimeHeader("MIME-Version", mimeVersion));
		for (Header header : part.getHeaders()) {
			if (!header.getName().equalsIgnoreCase("Content-Type") && !header.getName().equalsIgnoreCase("MIME-Version"))
				writeHeaders(output, header);
		}
		writeHeaders(output, contentType);
		finishHeaders(output);
		for (Part child : part) {
			writeBoundary(output, boundary, false);
			format((Part) child, output);
		}
		writeBoundary(output, boundary, true);
	}
	
	private void writeBoundary(WritableContainer<ByteBuffer> output, String boundary, boolean isLast) throws IOException {
		try {
			output.write(wrap(("--" + boundary + (isLast ? "--" : "") + "\r\n").getBytes("ASCII"), true));
		}
		catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
	
	public ContentTransferTranscoder getTranscoder() {
		if (transcoder == null)
			transcoder = new MimeContentTransferTranscoder();
		return transcoder;
	}

	public void setTranscoder(ContentTransferTranscoder transcoder) {
		this.transcoder = transcoder;
	}

	public String getMimeVersion() {
		return mimeVersion;
	}

	public void setMimeVersion(String mimeVersion) {
		this.mimeVersion = mimeVersion;
	}

	public boolean isAllowBinary() {
		return allowBinary;
	}

	public void setAllowBinary(boolean allowBinary) {
		this.allowBinary = allowBinary;
	}

	public int getChunkSize() {
		return chunkSize;
	}

	public void setChunkSize(int chunkSize) {
		this.chunkSize = chunkSize;
	}
}
