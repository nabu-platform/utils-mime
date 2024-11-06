/*
* Copyright (C) 2014 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

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
import be.nabu.utils.mime.api.ExpectContinueHandler;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.api.HeaderProvider;
import be.nabu.utils.mime.api.PartParser;
import be.nabu.utils.mime.util.ChunkedReadableByteContainer;
import be.nabu.utils.security.api.ManagedKeyStore;

/**
 * Any mime part starts with one or more headers (which can be length-encoded)
 * After the headers (and two linefeeds) you can optionally have content
 * 
 * TODO: have to rewrite this to use the same methods as the flat file parser to boost performance
 * 		> use the limited markable (on the byte stream!)
 * 		> use a bigger buffer to convert byte to char (or simply stick to bytes?)
 * 		> use the backed delimited (reads chunks instead of one by one)
 * 		> for http: how to make sure the remainder of this buffer is "pushed back" to the socket? > chain it with next request?
 * 			> need to update handling of linefeeds etc
 * 			> because we are using dynamic resource, the remainder will also be in that resource, is this a problem?
 * Is the buffering of the bytes already reading too much? or is default http request/response behavior making this work accidently?
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
	
	/**
	 * In the original implementation we would clean up any whitespace characters that appeared between boundaries in multiparts
	 * This however does NOT work well when using blocking I/O and a stream that is not yet closed, it will simply hang
	 * Presumably this was added for mail-based multiparts but it should not be used in a HTTP setting
	 * Because I don't just want to turn it off, I will leave the default to true and explicitly disable it in the http wrapper
	 */
	private boolean cleanupWhitespaceBetweenBoundaries = true;
	
	// this refers to rfc2616 article 4.4 Message Length option 5
	// i have only ever seen this in the wild in a single situation
	// the behavior (if you disable this) is that the parser assumes there is no content and you get an empty response
	private boolean allowNoMessageSizeForClosedConnections = false;
	
	/**
	 * The headers might include "Expect: 100-Continue" for HTTP
	 * If this is encountered, the expectContinueHandler is called to determine whether or not the parsing should continue
	 */
	private ExpectContinueHandler expectContinueHandler = null;
	
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
		return parse(resource, new Header[0]);
	}
	
	public ParsedMimePart parse(ReadableResource resource, Header...headers) throws ParseException, IOException {
		// you could process it as bytes but then we need to constantly convert it to chars when needed (boundary checking, header parsing,...)
		// note that the converter used maps bytes 1-1 to chars so the range is 0-256, the same range as the original code page 437
		// the following line initializes the data using the code page
//		ReadableCharContainer data = IOUtils.wrap(resource.getReadable(), Charset.forName("Cp437"));
		// but instead we opt for a simple byte-to-char converter
		// this is also partly because it was originally designed to only parse actual mime which remains in the ASCII range (7bit only, no extended range)
		// however when supporting http, binary support had to be added which means adding support for the eight bit, this is done by using either Cp437 or this straight conversion
//		ReadableContainer<CharBuffer> data = new ReadableStraightByteToCharContainer(IOUtils.bufferReadable(resource.getReadable(), IOUtils.newByteBuffer(1024*10, true)));
		ReadableContainer<CharBuffer> data = new ReadableStraightByteToCharContainer(resource.getReadable());
		try {
			return parse(IOUtils.countReadable(data), null, 0, resource, true, requireKnownContentLength, headers);
		}
		finally {
			data.close();
		}
	}
	
	ParsedMimePart parse(CountingReadableContainer<CharBuffer> data, ParsedMimeMultiPart parent, int partNumber, Header...headers) throws ParseException, IOException {
		return parse(data, parent, partNumber, null, false, requireKnownContentLength, headers);
	}
	
	private ParsedMimePart parse(CountingReadableContainer<CharBuffer> data, ParsedMimeMultiPart parent, int partNumber, ReadableResource resource, boolean isRoot, boolean requireKnownContentLength, Header...originalHeaders) throws ParseException, IOException {
		long initialOffset = data.getReadTotal();
		Header [] headers = originalHeaders == null || originalHeaders.length == 0 ? MimeUtils.readHeaders(data) : originalHeaders;
		String contentType = MimeUtils.getContentType(headers).toLowerCase();
		
		ParsedMimePart part = newHandler(contentType);
		part.setParser(this);
		part.setResource(resource);
		part.setHeader(headers);
		part.setOffset(initialOffset);
		part.setBodyOffset(data.getReadTotal() - initialOffset);
		part.setParent(parent, partNumber);
		
		Header expectHeader = MimeUtils.getHeader("Expect", headers);
		if (expectHeader != null && expectHeader.getValue().trim().equalsIgnoreCase("100-Continue")) {
			if (!isRoot)
				throw new ParseException("An 'Expect' header was found in a non-root part", 0);
			else if (!getExpectContinueHandler().shouldContinue(headers))
				return part;
		}
		
		// the boundary should not be null for classic multipart/ content types
		// however currently encrypted parts etc are also modeled as multiparts and they don't always have a boundary (e.g. when on root)
		// if there is no boundary, it will read till the end of the file
		String boundary = MimeUtils.getBoundary(headers);
		// it's a multipart, parse the children
		if (part instanceof ParsedMimeMultiPart && boundary != null) {
			ParsedMimeMultiPart multiPart = (ParsedMimeMultiPart) part;
			
			// @2023-11-30 when we call parse here, we don't take into account transfer encoding because we are not working on the root part
			// we do a small precalculation to see if the multipart is using chunked and wrap it in a dechunkifier if so
			// this is a late addition to the code which is why it is a localized patch rather than a full fix
			// where we call parseContentPart a few lines below, we used to pass in "data" directly
			ReadableContainer<CharBuffer> readableData = data;
			String transferEncoding = MimeUtils.getTransferEncoding(part.getHeaders());
			if (transferEncoding != null && transferEncoding.equalsIgnoreCase("chunked")) {
				HeaderProvider headerProvider = new ChunkedReadableByteContainer(
									new ReadableStraightCharToByteContainer(data));
				readableData = new ReadableStraightByteToCharContainer(headerProvider);
			}
			
			int childPartNumber = 0;
			// if it is a proper mime part with a boundary, it can start with some data which defaults to text/plain (if mime/multipart) because it has no headers of its own
			long possibleOffset = data.getReadTotal();
			// check for an initial content part with no headers
			// we also don't need a required known length here because we are using boundaries!
			ParsedMimePart contentPart = parseContentPart(readableData, boundary, false);
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
					// @2023-10-05 even in HTTP, if we have a multipart with child parts in it, the parent multipart _must_ have a content length/chunked indication
					// but child parts within the parent do not, they are delimited by the boundary of the multipart
					// so for child parsing, we explicitly disable the need for required content length
					ParsedMimePart child = parse(IOUtils.countReadable(IOUtils.delimit(data, "--" + boundary)), multiPart, childPartNumber++, null, false, false);
					child.setOffset(childOffset);
					multiPart.addParts(child);
					if (isLastBoundary(data))
						break;
				}
			}
			// need to set size on multipart
			multiPart.setSize(data.getReadTotal());
			if (cleanupWhitespaceBetweenBoundaries) {
				// copy any garbage between boundaries to the sink (should only be whitespace)
				IOUtils.copyChars(IOUtils.validate(data, allowedCharactersBetweenBoundaries, true), IOUtils.newCharSink());
			}
		}
		// it's binary content
		else {
			int amountToIgnore = parseContentPart(part, data, null, requireKnownContentLength, headers);
			// in the beginning we always used readTotal - amountToIgnore
			// the problem however is that the amountToIgnore mostly strips whitespace at the end
			// in a very few cases however the whitespace is not due to mime formatting but an actual part of the content
			// if that is the case and we _strip_ the whitespace, the reported content length will no longer be accurate for the actual content
			// because the content length assumes the whitespace to remain intact
			// that means that if we have a content length, we use that instead of the approximation with amount to ignore
			// the reverse proxy had this problem where a pdf had a trailing linefeed and when sending it to the target server (without altering the content-length), it was one byte short
			Long contentLength = MimeUtils.getContentLength(headers);
			if (contentLength != null) {
				part.setSize(part.getBodyOffset() + contentLength);
			}
			else {
				part.setSize(data.getReadTotal() - amountToIgnore);
			}
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
	
	private ParsedMimePart parseContentPart(ReadableContainer<CharBuffer> data, String boundary, boolean requireKnownContentLength, Header...headers) throws ParseException, IOException {
		ParsedMimePart part = newHandler(MimeUtils.getContentType(headers));
		part.setParser(this);
		CountingReadableContainer<CharBuffer> countingData = IOUtils.countReadable(data);
		// currently not applying same fix as with pure binary body for http
		// need to see an actual usecase of this before we do that
		int amountToIgnore = parseContentPart(part, countingData, boundary, requireKnownContentLength, headers);
		part.setSize(countingData.getReadTotal() - amountToIgnore);
		return part;
	}
	
	/**
	 * Returns the amount of chars to ignore
	 * @throws IOException 
	 */
	private int parseContentPart(ParsedMimePart part, ReadableContainer<CharBuffer> data, String boundary, boolean requireKnownContentLength, Header...headers) throws ParseException, IOException {
		// if we have chunked content, wrap it
		String transferEncoding = MimeUtils.getTransferEncoding(part.getHeaders());
		
		// the content part should be either terminated by a boundary, by a preset content length, by transfer encoding (chunked) or by the end of the data stream
		if (boundary != null)
			data = IOUtils.delimit(data, "--" + boundary);
		else {
			Long contentLength = MimeUtils.getContentLength(part.getHeaders());
			if (contentLength != null)
				data = IOUtils.blockUntilRead(IOUtils.limitReadable(data, contentLength), contentLength);
			else if (requireKnownContentLength) {
				// instead of throwing an error, handle it like it has no size
				if (transferEncoding == null || !transferEncoding.equalsIgnoreCase("chunked")) {
					boolean allowAnyway = false;
					// an exceptional case
					if (allowNoMessageSizeForClosedConnections) {
						Header connection = MimeUtils.getHeader("Connection", part.getHeaders());
						if (connection != null && connection.getValue() != null && connection.getValue().equalsIgnoreCase("close")) {
							allowAnyway = true;
						}
					}
					
					if (!allowAnyway) {
						return 0;
					}
				}
//					throw new ParseException("Can not parse a root content part of unknown length. You can toggle requireKnownContentLength to bypass this", 0);
			}
		}

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

	public ExpectContinueHandler getExpectContinueHandler() {
		if (expectContinueHandler == null)
			expectContinueHandler = new AlwaysContinue();
		return expectContinueHandler;
	}

	public void setExpectContinueHandler(ExpectContinueHandler expectContinueHandler) {
		this.expectContinueHandler = expectContinueHandler;
	}

	public boolean isAllowNoMessageSizeForClosedConnections() {
		return allowNoMessageSizeForClosedConnections;
	}

	public void setAllowNoMessageSizeForClosedConnections(boolean allowNoMessageSizeForClosedConnections) {
		this.allowNoMessageSizeForClosedConnections = allowNoMessageSizeForClosedConnections;
	}

	public boolean isCleanupWhitespaceBetweenBoundaries() {
		return cleanupWhitespaceBetweenBoundaries;
	}

	public void setCleanupWhitespaceBetweenBoundaries(boolean cleanupWhitespaceBetweenBoundaries) {
		this.cleanupWhitespaceBetweenBoundaries = cleanupWhitespaceBetweenBoundaries;
	}
	
}
