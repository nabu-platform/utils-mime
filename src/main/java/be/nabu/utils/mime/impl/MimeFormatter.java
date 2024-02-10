package be.nabu.utils.mime.impl;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import be.nabu.libs.resources.api.Resource;
import be.nabu.utils.io.IOUtils;
import static be.nabu.utils.io.IOUtils.*;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.api.WritableContainer;
import be.nabu.utils.mime.api.ContentPart;
import be.nabu.utils.mime.api.ContentTransferTranscoder;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.api.ModifiablePart;
import be.nabu.utils.mime.api.MultiPart;
import be.nabu.utils.mime.api.Part;
import be.nabu.utils.mime.api.PartFormatter;
import be.nabu.utils.mime.util.ChunkedWritableByteContainer;

/**
 * This formatter works with either fully correct messages or with modifiable parts so it can add headers where needed
 */
public class MimeFormatter implements PartFormatter {

	private String mimeVersion = "1.0";
	private ContentTransferTranscoder transcoder;
	private Set<String> headersToIgnore = new HashSet<String>();
	private boolean foldHeader = true;
	// backwards compatible
	private HeaderEncoding headerEncoding = HeaderEncoding.RFC2047;
	private boolean disableContentEncoding = false;
	
	/**
	 * We use boundaries with "reserved" characters, this is actually intentional and advised because that way it can't conflict with correct content
	 * However because of that we have to quote the boundary
	 * https://www.ietf.org/rfc/rfc1341.txt 
	 */
	private boolean quoteBoundary = true;
	
	/**
	 * For mails, binary data is not allowed so we need to encode it
	 * However for http binary is not a problem, set this boolean to true to allow binary streams
	 */
	protected boolean allowBinary = false;
	
	private boolean includeMainContentTrailingLineFeeds = true;
	
	/**
	 * Whether or not to optimize the compression at the cost of performance
	 */
	private boolean optimizeCompression;
	
	/**
	 * The default chunk size is 50kb
	 * If it is too small, the download may actually be too slow
	 */
	private int chunkSize = 1024 * 50;
	
	private List<String> quotableContentTypes = new ArrayList<String>(); {
		quotableContentTypes.add("text/plain");
		quotableContentTypes.add("text/xml");
		quotableContentTypes.add("text/html");
		quotableContentTypes.add("application/xml");
	}
	
	private List<String> unencodedContentTypes = new ArrayList<String>(); {
		unencodedContentTypes.add("application/x-www-form-urlencoded");
	}
	
	public void ignoreHeaders(String...names) {
		for (String name : names) {
			headersToIgnore.add(name.toLowerCase());
		}
	}
	
	public void formatHeaders(Part part, WritableContainer<ByteBuffer> output) throws IOException, FormatException {
		if (part instanceof FormattablePart)
			throw new FormatException("The parser currently does not support partial formatting of custom formatted parts like " + part.getClass().getName());
		else if (isMultiPart(part))
			formatMultiPartHeaders((MultiPart) part, output);
		else if (part instanceof ContentPart)
			formatContentPartHeaders((ContentPart) part, output);
		else
			throw new FormatException("Could not format part of type " + part.getClass().getName() + ", it is not a content part and not a multipart");
	}
	
	public void formatContent(Part part, WritableContainer<ByteBuffer> output) throws IOException, FormatException {
		if (part instanceof FormattablePart)
			throw new FormatException("The parser currently does not support partial formatting of custom formatted parts like " + part.getClass().getName());
		else if (isMultiPart(part))
			formatMultiPartContent((MultiPart) part, output);
		else if (part instanceof ContentPart)
			formatContentPartContent((ContentPart) part, output);
		else
			throw new FormatException("Could not format part of type " + part.getClass().getName() + ", it is not a content part and not a multipart");
	}
	
	@Override
	public void format(Part part, WritableContainer<ByteBuffer> output) throws IOException, FormatException {
		if (part instanceof FormattablePart) {
			((FormattablePart) part).setFormatter(this);
			((FormattablePart) part).format(output);
		}
		else if (isMultiPart(part)) {
			formatMultiPartHeaders((MultiPart) part, output);
			formatMultiPartContent((MultiPart) part, output);
		}
		else if (part instanceof ContentPart) {
			formatContentPartHeaders((ContentPart) part, output);
			formatContentPartContent((ContentPart) part, output);
		}
		else
			throw new FormatException("Could not format part of type " + part.getClass().getName() + ", it is not a content part and not a multipart");
	}
	
	protected boolean isMultiPart(Part part) {
		String contentType = MimeUtils.getContentType(part.getHeaders()).toLowerCase();
		return part instanceof MultiPart && (contentType.startsWith("multipart/") || contentType.equals(Resource.CONTENT_TYPE_DIRECTORY) || Resource.CONTENT_TYPE_DIRECTORY.equals(part.getContentType()));
	}
	
	protected String getContentTransferEncoding(Part part) {
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
	
	private void writeHeaders(WritableContainer<ByteBuffer> output, Header...headers) throws IOException {
		for (Header header : headers) {
			String formatted = MimeUtils.format(header, foldHeader, headerEncoding);
			output.write(wrap((formatted + "\r\n").getBytes("ASCII"), true));
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
	
	protected void formatContentPartHeaders(ContentPart part, WritableContainer<ByteBuffer> output) throws IOException, FormatException {
		String contentTransferEncoding = MimeUtils.getContentTransferEncoding(part.getHeaders());
		List<Header> headers = new ArrayList<Header>();
		// make an educated guess
		for (Header header : part.getHeaders()) {
			if (!headersToIgnore.contains(header.getName().toLowerCase())) {
				headers.add(header);
			}
		}
		// add it after the existing headers to be compliant with previous implementation
		if (!allowBinary && contentTransferEncoding == null) {
			contentTransferEncoding = getContentTransferEncoding(part);
			if (contentTransferEncoding != null) {
				headers.add(new MimeHeader("Content-Transfer-Encoding", contentTransferEncoding));
			}
		}
		writeHeaders(output, headers.toArray(new Header[0]));
		finishHeaders(output);
	}
	
	private void formatContentPartContent(ContentPart part, WritableContainer<ByteBuffer> output) throws IOException {
		
		ReadableContainer<ByteBuffer> content = part.getReadable();
		if (content != null) {
			try {
				content = limitByContentRange(part, content);
				
				WritableContainer<ByteBuffer> encodedOutput = encodeOutput(part, output);
				copyBytes(content, encodedOutput);
				encodedOutput.flush();
				if (part.getParent() != null || includeMainContentTrailingLineFeeds) {
					output.write(wrap("\r\n\r\n".getBytes("ASCII"), true));
				}
			}
			catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
			finally {
				content.close();
			}
		}
	}

	protected WritableContainer<ByteBuffer> encodeOutput(Part part, WritableContainer<ByteBuffer> output) {
		// this assumes the formateContentPartHeaders has been called which will have checked or set the encoding (or thrown an exception)
		String contentTransferEncoding = MimeUtils.getContentTransferEncoding(part.getHeaders());
		String transferEncoding = MimeUtils.getTransferEncoding(part.getHeaders());
		String contentEncoding = MimeUtils.getContentEncoding(part.getHeaders());
		
		if (!allowBinary && contentTransferEncoding == null && !(part instanceof MultiPart)) {
			contentTransferEncoding = getContentTransferEncoding(part);
		}
		// you can do two things here:
		// encode the input as you are reading, however this presumes the input returns a clean -1 which would trigger the "flush" in the transcoder
		// or you can encode the output as you are writing so you can manually flush
		// the second option is of course preferable
		// first apply actual transfer encoding (if necessary). This is mostly for chunking but can also be gzip etc
		// to make matters slightly muddier, the values for content encoding are +- the same as for transfer encoding (they are both http constructs)
		// the values for contentTransferEncoding are different as they are aimed at mime
		WritableContainer<ByteBuffer> encodedOutput = getTranscoder().encodeContent(transferEncoding, output);
		if (encodedOutput instanceof ChunkedWritableByteContainer) {
			((ChunkedWritableByteContainer) encodedOutput).setWriteEnding(!includeMainContentTrailingLineFeeds);
			encodedOutput = bufferWritable(encodedOutput, newByteBuffer(chunkSize, true));
		}
		// then apply content transfer encoding, it allows for base64 etc, it is usually not combined with transfer-encoding in the above
		encodedOutput = getTranscoder().encodeTransfer(contentTransferEncoding, encodedOutput);
		// last but not least: content-encoding. this is end-to-end instead of hop-to-hop
		// in other words, a transfer-encoding gzip can be unzipped by an intermediate server while a content-encoding gzip should be unzipped by the client
		if (!disableContentEncoding) {
			encodedOutput = getTranscoder().encodeContent(contentEncoding, encodedOutput);
		}
		return encodedOutput;
	}

	protected ReadableContainer<ByteBuffer> limitByContentRange(ContentPart part, ReadableContainer<ByteBuffer> content) throws IOException {
		String contentRange = MimeUtils.getContentRange(part.getHeaders());
		if (contentRange != null) {
			if (contentRange.trim().startsWith("bytes")) {
				contentRange = contentRange.trim().substring("bytes".length()).trim();
			}
			// format: from-to/total; from & to are inclusive!
			int indexHyphen = contentRange.indexOf("-");
			int indexSlash = contentRange.indexOf("/");
			if (indexHyphen < 0 || indexSlash < 0)
				throw new IllegalArgumentException("The content-range header is misformed, it should be off the format 'from-to/total'");
			long from = Long.parseLong(contentRange.substring(0, indexHyphen));
			long to = Long.parseLong(contentRange.substring(indexHyphen + 1, indexSlash));
			// a potentially optimized version
			if (from > 0) {
				IOUtils.skipBytes(content, from);
			}
//			content.read(newByteSink(from));
			// the to is inclusive!
			content = IOUtils.limitReadable(content, to + 1);
			// update the content-length if we can/should
			if (part instanceof ModifiablePart) {
				Long contentLength = MimeUtils.getContentLength(part.getHeaders());
				// again: to is inclusive!
				if (contentLength != null && contentLength != (to - from) + 1) {
					((ModifiablePart) part).removeHeader("Content-Length");
					((ModifiablePart) part).setHeader(new MimeHeader("Content-Length", "" + ((to - from) + 1)));
				}
			}
		}
		return content;
	}
	
	protected void formatMultiPartHeaders(MultiPart part, WritableContainer<ByteBuffer> output) throws IOException, FormatException {
		Header contentType = MimeUtils.getHeader("Content-Type", part.getHeaders());
		// we need a contentType with a boundary, otherwise it will have to be set/updated
		if (contentType == null || MimeUtils.getBoundary(contentType) == null) {
			// need a MimeHeader instance because we need specific methods like addComment
			MimeHeader newContentType = null;
			if (contentType == null)
				newContentType = new MimeHeader("Content-Type", "multipart/mixed");
			else if (contentType instanceof MimeHeader)
				newContentType = (MimeHeader) contentType;
			else
				newContentType = new MimeHeader(contentType.getName(), contentType.getValue(), contentType.getComments());

			if (quoteBoundary) {
				newContentType.addComment("boundary=\"-=part." + MimeUtils.generateBoundary() + "\"");
			}
			else {
				newContentType.addComment("boundary=-=part." + MimeUtils.generateBoundary());
			}

			// we only need to update the multipart if we have to set a new header, otherwise the header itself is simply adjusted
			if (!newContentType.equals(contentType)) {
				if (part instanceof ModifiablePart) {
					if (contentType != null)
						((ModifiablePart) part).removeHeader("Content-Type");
					((ModifiablePart) part).setHeader(newContentType);
				}
				else
					throw new FormatException("The multipart has no content-type or it is not complete (no boundary) and the part is not modifiable so the formatter could not set it");
				contentType = newContentType;
			}
		}
		
		writeHeaders(output, new MimeHeader("MIME-Version", mimeVersion));
		for (Header header : part.getHeaders()) {
			if (!header.getName().equalsIgnoreCase("MIME-Version") && !headersToIgnore.contains(header.getName().toLowerCase())) {
				writeHeaders(output, header);
			}
		}
		finishHeaders(output);
	}
	
	private void formatMultiPartContent(MultiPart part, WritableContainer<ByteBuffer> output) throws IOException, FormatException {
		output = encodeOutput(part, output);
		Header contentType = MimeUtils.getHeader("Content-Type", part.getHeaders());
		if (contentType == null)
			throw new FormatException("No content-type found for multipart");
		String boundary = MimeUtils.getBoundary(contentType);
		if (boundary == null)
			throw new FormatException("No boundary found for multipart");
		for (Part child : part) {
			writeBoundary(output, boundary, false);
			format((Part) child, output);
		}
		writeBoundary(output, boundary, true);		
	}
	
	protected void writeBoundary(WritableContainer<ByteBuffer> output, String boundary, boolean isLast) throws IOException {
		try {
			output.write(wrap(("--" + boundary + (isLast ? "--" : "") + "\r\n").getBytes("ASCII"), true));
		}
		catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
	
	public ContentTransferTranscoder getTranscoder() {
		if (transcoder == null) {
			transcoder = new MimeContentTransferTranscoder(optimizeCompression);
		}
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

	public boolean isIncludeMainContentTrailingLineFeeds() {
		return includeMainContentTrailingLineFeeds;
	}

	public void setIncludeMainContentTrailingLineFeeds(boolean includeMainContentTrailingLineFeeds) {
		this.includeMainContentTrailingLineFeeds = includeMainContentTrailingLineFeeds;
	}

	public boolean isOptimizeCompression() {
		return optimizeCompression;
	}

	public void setOptimizeCompression(boolean optimizeCompression) {
		this.optimizeCompression = optimizeCompression;
	}

	public boolean isFoldHeader() {
		return foldHeader;
	}

	public void setFoldHeader(boolean foldHeader) {
		this.foldHeader = foldHeader;
	}

	public HeaderEncoding getHeaderEncoding() {
		return headerEncoding;
	}

	public void setHeaderEncoding(HeaderEncoding headerEncoding) {
		this.headerEncoding = headerEncoding;
	}

	public boolean isQuoteBoundary() {
		return quoteBoundary;
	}

	public void setQuoteBoundary(boolean quoteBoundary) {
		this.quoteBoundary = quoteBoundary;
	}

	public boolean isDisableContentEncoding() {
		return disableContentEncoding;
	}

	public void setDisableContentEncoding(boolean disableContentEncoding) {
		this.disableContentEncoding = disableContentEncoding;
	}
}
