package be.nabu.utils.mime.impl;

import java.io.IOException;
import java.security.KeyStore;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.CharBuffer;
import be.nabu.utils.io.api.CountingReadableContainer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.containers.TrailingContainer;
import be.nabu.utils.io.containers.bytes.ReadableStraightCharToByteContainer;
import be.nabu.utils.io.containers.chars.ReadableStraightByteToCharContainer;
import be.nabu.utils.mime.api.ContentTransferTranscoder;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.api.HeaderProvider;
import be.nabu.utils.mime.api.PartParser;
import be.nabu.utils.mime.util.ChunkedReadableByteContainer;
import be.nabu.utils.security.api.ManagedKeyStore;

/**
 * Any mime part starts with one or more headers (which can be length-encoded)
 * After the headers (and two linefeeds) you can optionally have content
 */
public class MimeParser implements PartParser {

	/**
	 * The keystore must be managed to provide passwords
	 */
	private ManagedKeyStore keyStore;
	
	private KeyStore trustStore;
	
	private char [] allowedCharactersBetweenBoundaries = new char [] { '\n', '\r', ' ', '\t' };
	
	/**
	 * if we have binary content, we need to check if we require a fixed size
	 * the fixed size requirement is only necessary for root content parts, child parts are delimited by boundaries from the multipart
	 * it is also only important if multiple requests can/will come from the same source (e.g. persistent http connections)
	 * fixed size can be indicated in one of two ways: chunked transfer encoding & Content-Length header
	 * It is set to false by default because even when reusing connections, it assumes that the next part will not be there immediately
	 * if it were, it would be parsed as part of the first request, otherwise the parser would stop anyway
	 */
	private boolean requireKnownContentLength = false;
	
	private Map<String, Class<? extends ParsedMimePart>> typeHandlers = new HashMap<String, Class<? extends ParsedMimePart>>(); {
		typeHandlers.put("application/x-www-form-urlencoded", ParsedMimeFormPart.class);
		typeHandlers.put("application/www-form-urlencoded", ParsedMimeFormPart.class);
		
		typeHandlers.put("application/x-pkcs7-mime", ParsedPKCS7MimeMultiPart.class);
		typeHandlers.put("application/pkcs7-mime", ParsedPKCS7MimeMultiPart.class);
		
		typeHandlers.put("application/x-pkcs7-signature", ParsedSignedMimePart.class);
		typeHandlers.put("application/pkcs7-signature", ParsedSignedMimePart.class);
	}

	/**
	 * The amount of trailing whitespace we trim from content parts
	 */
	private int trimSize = 10;
	
	/**
	 * The transcoder used (if any)
	 */
	private ContentTransferTranscoder transcoder;
	
	public ContentTransferTranscoder getTranscoder() {
		if (transcoder == null)
			transcoder = new MimeContentTransferTranscoder();
		return transcoder;
	}

	public void setTranscoder(ContentTransferTranscoder transcoder) {
		this.transcoder = transcoder;
	}

	@Override
	public ParsedMimePart parse(ReadableResource resource) throws ParseException, IOException {
		// you could process it as bytes but then we need to constantly convert it to chars when needed (boundary checking, header parsing,...)
		// note that the converter used maps bytes 1-1 to chars so the range is 0-256, the same range as the original code page 437
		// the following line initializes the data using the code page
//		ReadableCharContainer data = IOUtils.wrap(resource.getReadable(), Charset.forName("Cp437"));
		// but instead we opt for a simple byte-to-char converter
		// this is also partly because it was originally designed to only parse actual mime which remains in the ASCII range (7bit only, no extended range)
		// however when supporting http, binary support had to be added which means adding support for the eight bit, this is done by using either Cp437 or this straight conversion
		ReadableContainer<CharBuffer> data = new ReadableStraightByteToCharContainer(IOUtils.bufferReadable(resource.getReadable(), IOUtils.newByteBuffer(1024*10, true)));
		try {
			return parse(IOUtils.countReadable(data), null, 0, resource);
		}
		finally {
			data.close();
		}
	}
	
	ParsedMimePart parse(CountingReadableContainer<CharBuffer> data, ParsedMimeMultiPart parent, int partNumber) throws ParseException, IOException {
		return parse(data, parent, partNumber, null);
	}
	
	/**
	 * The URI has to be passed in so it can be set on the part (atm only the root part)
	 * This is necessary because we allow for a nested parse right at the end of this method
	 * This nested parse requires the URI to rebuild the content
	 * @throws IOException 
	 */
	private ParsedMimePart parse(CountingReadableContainer<CharBuffer> data, ParsedMimeMultiPart parent, int partNumber, ReadableResource resource) throws ParseException, IOException {
		long initialOffset = data.getReadTotal();
		Header [] headers = MimeUtils.readHeaders(data);
		String contentType = MimeUtils.getContentType(headers).toLowerCase();
		
		ParsedMimePart part = newHandler(contentType);
		part.setParser(this);
		part.setResource(resource);
		part.setHeader(headers);
		part.setOffset(initialOffset);
		part.setBodyOffset(data.getReadTotal() - initialOffset);
		part.setParent(parent, partNumber);
		// the boundary should not be null for classic multipart/ content types
		// however currently encrypted parts etc are also modeled as multiparts and they don't always have a boundary (e.g. when on root)
		// if there is no boundary, it will read till the end of the file
		String boundary = MimeUtils.getBoundary(headers);
		// it's a multipart, parse the children
		if (part instanceof ParsedMimeMultiPart && boundary != null) {
			ParsedMimeMultiPart multiPart = (ParsedMimeMultiPart) part;
			int childPartNumber = 0;
			// if it is a proper mime part with a boundary, it can start with some data which defaults to text/plain (if mime/multipart) because it has no headers of its own
			long possibleOffset = data.getReadTotal();
			// check for an initial content part with no headers
			ParsedMimePart contentPart = parseContentPart(data, boundary);
			// the part is optional, if there is no data there, don't add it
			if (contentPart.getSize() > 0) {
				contentPart.setOffset(possibleOffset);
				contentPart.setParent(multiPart, childPartNumber++);
				multiPart.addParts(contentPart);
			}
			// the above bit will have read to a boundary, check if it is the last, if not, parse the multipart
			if (!isLastBoundary(data)) {
				while(true) {
					long childOffset = data.getReadTotal();
					ParsedMimePart child = parse(IOUtils.countReadable(IOUtils.delimit(data, "--" + boundary)), multiPart, childPartNumber++);
					child.setOffset(childOffset);
					multiPart.addParts(child);
					if (isLastBoundary(data))
						break;
				}
			}
			// need to set size on multipart
			multiPart.setSize(data.getReadTotal());
			// copy any garbage between boundaries to the sink (should only be whitespace)
			IOUtils.copyChars(IOUtils.validate(data, allowedCharactersBetweenBoundaries, true), IOUtils.newCharSink());
		}
		// it's binary content
		else {
			int amountToIgnore = parseContentPart(part, data, null, headers);
			part.setSize(data.getReadTotal() - amountToIgnore);
		}
		
		// after all is set, continue parse if necessary
		if (part instanceof ParseablePart)
			((ParseablePart) part).parse();
		
		return part;
	}
	
	/**
	 * This assumes you ran getContentSize() first which has read the boundary itself
	 * The cursor is then situated at the end of the boundary
	 * @throws ParseException 
	 * @throws IOException 
	 */
	private boolean isLastBoundary(ReadableContainer<CharBuffer> data) throws ParseException, IOException {
		char [] singleChar = new char[1];
		int trailingCounter = 0;
		long amountRead = 0;
		while((amountRead = data.read(IOUtils.wrap(singleChar, false))) == 1) {
			if (singleChar[0] == '-') {
				trailingCounter++;
				if (trailingCounter > 2)
					throw new ParseException("The boundary can be followed by max two '-'", 0);
			}
			// should be followed by linefeed
			else if (singleChar[0] == '\r')
				continue;
			else if (singleChar[0] == '\n')
				break;
			else
				throw new ParseException("The boundary should not be followed by " + singleChar[0] + IOUtils.toString(data), 0);
		}
		return trailingCounter > 0 || amountRead == -1;
	}
	
	private ParsedMimePart parseContentPart(ReadableContainer<CharBuffer> data, String boundary, Header...headers) throws ParseException, IOException {
		ParsedMimePart part = newHandler(MimeUtils.getContentType(headers));
		part.setParser(this);
		CountingReadableContainer<CharBuffer> countingData = IOUtils.countReadable(data);
		int amountToIgnore = parseContentPart(part, countingData, boundary, headers);
		part.setSize(countingData.getReadTotal() - amountToIgnore);
		return part;
	}
	
	/**
	 * Returns the amount of chars to ignore
	 * @throws IOException 
	 */
	private int parseContentPart(ParsedMimePart part, ReadableContainer<CharBuffer> data, String boundary, Header...headers) throws ParseException, IOException {
		// the content part should be either terminated by a boundary, by a preset content length, by transfer encoding (chunked) or by the end of the data stream
		if (boundary != null)
			data = IOUtils.delimit(data, "--" + boundary);
		else {
			Long contentLength = MimeUtils.getContentLength(part.getHeaders());
			if (contentLength != null)
				data = IOUtils.blockUntilRead(IOUtils.limitReadable(data, contentLength), contentLength);
			else if (requireKnownContentLength) {
				String transferEncoding = MimeUtils.getTransferEncoding(headers);
				if (transferEncoding == null || !transferEncoding.equalsIgnoreCase("chunked"))
					throw new ParseException("Can not parse a root content part of unknown length. You can toggle requireKnownContentLength to bypass this", 0);
			}
		}

		// if we have chunked content, wrap it
		String transferEncoding = MimeUtils.getTransferEncoding(part.getHeaders());
		// we also have to capture the headers at the end
		HeaderProvider headerProvider = null;
		if (transferEncoding != null && transferEncoding.equalsIgnoreCase("chunked")) {
			headerProvider = new ChunkedReadableByteContainer(
								new ReadableStraightCharToByteContainer(data));
			data = new ReadableStraightByteToCharContainer(headerProvider);
		}
		
		// we need to keep track of the tail of the data, there _should_ be two linefeeds (\r\n) be before the boundary that can be safely ignored
		// additionally we have a configurable trimsize
		TrailingContainer<CharBuffer> trailer = new TrailingContainer<CharBuffer>(data, 4 + trimSize);
		IOUtils.copyChars(trailer, IOUtils.newCharSink());
		String trailing = IOUtils.toString(trailer.getTrailing());
		String trailingWithoutLinefeeds = trailing.replaceAll("[\\s]+$", "");
		// this skips the ending linefeeds
		if (headerProvider != null)
			part.setHeader(headerProvider.getAdditionalHeaders());
		// the boundary is preceeded by two "--" so ignore an additional 2
		return trailing.length() - trailingWithoutLinefeeds.length() + (boundary == null ? 0 : boundary.length() + 2);
	}	
	
	public ManagedKeyStore getKeyStore() {
		return keyStore;
	}

	public void setKeyStore(ManagedKeyStore keyStore) {
		this.keyStore = keyStore;
	}

	public KeyStore getTrustStore() {
		return trustStore;
	}

	public void setTrustStore(KeyStore trustStore) {
		this.trustStore = trustStore;
	}

	public int getTrimSize() {
		return trimSize;
	}

	public void setTrimSize(int trimSize) {
		this.trimSize = trimSize;
	}
	
	public void setContentTypeHandler(String contentType, Class<? extends ParsedMimePart> handler) {
		typeHandlers.put(contentType.toLowerCase(), handler);
	}
	
	public Class<? extends ParsedMimePart> getHandler(String contentType) {
		Class<? extends ParsedMimePart> handler = typeHandlers.get(contentType.toLowerCase());
		if (handler == null) {
			if (contentType == null || !contentType.startsWith("multipart/"))
				handler = ParsedMimeBinaryPart.class;
			else
				handler = ParsedMimeMultiPart.class;
		}
		return handler;
	}
	
	ParsedMimePart newHandler(String contentType) {
		try {
			return getHandler(contentType).newInstance();
		}
		catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
		catch (InstantiationException e) {
			throw new RuntimeException(e);
		}
	}

	public boolean isRequireKnownContentLength() {
		return requireKnownContentLength;
	}

	public void setRequireKnownContentLength(boolean requireKnownContentLength) {
		this.requireKnownContentLength = requireKnownContentLength;
	}
}
