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

import static be.nabu.utils.io.IOUtils.wrap;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import be.nabu.utils.codec.api.Transcoder;
import be.nabu.utils.codec.impl.Base64Decoder;
import be.nabu.utils.codec.impl.Base64Encoder;
import be.nabu.utils.codec.impl.QuotedPrintableDecoder;
import be.nabu.utils.codec.impl.QuotedPrintableEncoder;
import be.nabu.utils.codec.impl.QuotedPrintableEncoding;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.CharBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.api.WritableContainer;
import be.nabu.utils.mime.api.ContentPart;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.api.ModifiableContentPart;
import be.nabu.utils.mime.api.ModifiablePart;
import be.nabu.utils.mime.api.MultiPart;
import be.nabu.utils.mime.api.Part;
import be.nabu.utils.security.SignatureType;
import be.nabu.utils.security.api.ManagedKeyStore;

/**
 * Content-Disposition is described in RFC2183
 * Content-Type is described in RFC2045
 */
public class MimeUtils {

	public static boolean isDeflatable(String contentType) {
		if (contentType == null) {
			return true;
		}
		else if (contentType.startsWith("image/")) {
			return contentType.equals("image/bmp");
		}
		else if (contentType.startsWith("video/")) {
			return false;
		}
		else if (contentType.startsWith("audio/")) {
			return contentType.equals("audio/wav") || contentType.equals("flac");
		}
		return true;
	}
	
	public static Part encrypt(Part part, X509Certificate...recipients) {
		return new FormattedEncryptedMimePart(part, recipients);
	}
	
	public static ModifiablePart wrapModifiable(Part part) {
		if (part instanceof ModifiablePart)
			return (ModifiablePart) part;
		else if (part instanceof ContentPart)
			return new ModifiableWrappedContentPart((ContentPart) part);
		else if (part instanceof MultiPart)
			return new ModifiableWrappedMultiPart((MultiPart) part);
		else
			throw new IllegalArgumentException("Can't wrap this type of part: " + part.getClass().getName());
	}
	
	public static Part compress(Part part) {
		return new FormattedCompressedMimePart(part);
	}
	
	public static MultiPart sign(Part partToSign, SignatureType signatureType, ManagedKeyStore keyStore, String...aliases) {
		return new FormattedSignedMimeMultiPart(partToSign, signatureType, keyStore, aliases);
	}
	
	public static void setReopenable(Part part, boolean reopenable) {
		if (part instanceof ModifiableContentPart) {
			((ModifiableContentPart) part).setReopenable(true);
		}
		else if (part instanceof MultiPart) {
			for (Part child : (MultiPart) part) {
				setReopenable(child, reopenable);
			}
		}
	}
	
	public static boolean isReopenable(Part part) {
		// content parts must be explicitly marked as reopenable
		if (part instanceof ContentPart && ((ContentPart) part).isReopenable()) {
			return true;
		}
		// for a multipart, all its child parts must be reopenable
		else if (part instanceof MultiPart) {
			boolean reopenable = true;
			for (Part child : (MultiPart) part) {
				reopenable &= isReopenable(child);
			}
			return reopenable;
		}
		// we assume default false
		return false;
	}
	
	public static Map<String, String> getHeaderAsValues(String name, Header...headers) {
		Map<String, String> values = new HashMap<String, String>();
		Header header = getHeader(name, headers);
		if (header != null) {
			// check if the value itself is also a key/value, for example ICAP with X-Infection-Found header:
			// X-Infection-Found: Type=0; Resolution=2; Threat=Win.Test.EICAR_HDB-1;
			int indexOf = header.getValue().indexOf('=');
			if (indexOf > 0) {
				values.put(header.getValue().substring(0, indexOf).toLowerCase().trim(), header.getValue().substring(indexOf + 1).trim().replaceAll("^\"", "").replaceAll("\"$", ""));
			}
			else {
				values.put("value", header.getValue().toLowerCase().trim());
			}
			if (header.getComments() != null) {
				for (String comment : header.getComments()) {
					int index = comment.indexOf("=");
					// invalid parameter
					if (index < 0)
						continue;
					String [] parts = comment.split("[\\s]*=[\\s]*");
					
					if (parts.length <= 1)
						continue;
					
					// the value can be quoted, the quotes must be ignored
					values.put(comment.substring(0, index).toLowerCase().trim(), comment.substring(index + 1).trim().replaceAll("^\"", "").replaceAll("\"$", ""));
				}
			}
		}
		return values;
	}
	
	public static List<String> getAcceptedEncodings(Header...headers) {
		return getValues("Accept-Encoding", headers);
	}
	
	public static List<String> getAcceptedLanguages(Header...headers) {
		return getValues("Accept-Language", headers);
	}
	
	public static List<String> getAcceptedCharsets(Header...headers) {
		return getValues("Accept-Charset", headers);
	}
	
	public static List<String> getAcceptedContentTypes(Header...headers) {
		return getValues("Accept", headers);
	}
	
	public static boolean contains(String name, String value, Header...headers) {
		List<String> values = getValues(name, headers);
		if (values != null) {
			for (String single : values) {
				if (value.trim().equalsIgnoreCase(single.trim())) {
					return true;
				}
			}
		}
		return false;
	}
	
	public static List<String> getValues(String name, Header...headers) {
		Header header = getHeader(name, headers);
		List<String> accepted = new ArrayList<String>();
		if (header != null) {
			for (String value : header.getValue().split("[\\s]*,[\\s]*")) {
				accepted.add(value.replaceAll(";.*", "").toLowerCase().trim());
			}
		}
		return accepted;
	}
	
	public static Header getHeader(String name, Header...headers) {
		Header [] result = getHeaders(name, headers);
		return result.length > 0 ? result[0] : null;
	}
	
	public static Header [] getHeaders(String name, Header...headers) {
		List<Header> result = new ArrayList<Header>();
		for (Header header : headers) {
			if (header.getName().equalsIgnoreCase(name))
				result.add(header);
		}
		return result.toArray(new Header[result.size()]);
	}
	
	public static String generateBoundary() {
		// this may leak information about the original system so let's hash it
		String uuid = UUID.randomUUID().toString();
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA1");
			byte [] result = digest.digest(uuid.getBytes("ASCII"));
			StringBuilder builder = new StringBuilder();
			for (int i = 0; i < result.length; i++)
				builder.append(Integer.toHexString(result[i] & 0xff));
			return builder.toString();
		}
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static String getBoundary(Header...headers) {
		return getHeaderAsValues("Content-Type", headers).get("boundary");
	}
	
	public static String getTransferEncoding(Header...headers) {
		return getHeaderAsValues("Transfer-Encoding", headers).get("value");
	}
	
	public static String getContentTransferEncoding(Header...headers) {
		return getHeaderAsValues("Content-Transfer-Encoding", headers).get("value");
	}
	
	public static String getContentEncoding(Header...headers) {
		Header header = getHeader("Content-Encoding", headers);
		return header == null ? null : header.getValue().trim();
	}
	
	// should refactor to live in httputils, needs the serverheader for unification
	public static String getCorrelationId(Header...headers) {
		Header header = getHeader("X-Correlation-Id", headers);
		return header == null ? null : header.getValue().trim();
	}
	
	public static Long getContentLength(Header...headers) {
		Header header = getHeader("Content-Length", headers);
		return header == null ? null : new Long(header.getValue().trim());
	}
	
	public static String getContentType(Header...headers) {
		Header header = getHeader("Content-Type", headers);
		// the proper default is as per RFC822 text/plain
		return header == null ? "text/plain" : header.getValue().trim().replaceAll(";.*$", "");
	}
	
	public static String getName(Header...headers) {
		// the "proper" way to send along a filename is to send it in the content-disposition
		String name = getHeaderAsValues("Content-Disposition", headers).get("filename");
		
		// however a lot of clients don't use this (yet) and instead put it in the content-type
		if (name == null)
			name = getHeaderAsValues("Content-Type", headers).get("name");
		
		return name;
	}
	
	public static String getFormName(Header...headers) {
		return getHeader("Content-Disposition", headers).getValue().equals("form-data") ? getHeaderAsValues("Content-Disposition", headers).get("name") : null;
	}

	public static void format(Part part, WritableContainer<ByteBuffer> output) {
		// TODO: write the data, encode where necessary, decode where necessary, recurse where necessary
		writeHeaders(part, output);
	}
	
	private static void writeHeaders(Part part, WritableContainer<ByteBuffer> output) {
		// TODO: write headers to the output
	}
	
	public static Transcoder<ByteBuffer> getEncoder(String contentTransferEncoding) {
		// technically for 7bit and 8bit we should do some proper linefeeding etc
		if (contentTransferEncoding == null || contentTransferEncoding.equalsIgnoreCase("7bit") || contentTransferEncoding.equalsIgnoreCase("8bit") || contentTransferEncoding.equalsIgnoreCase("binary"))
			return null;
		else if (contentTransferEncoding.equalsIgnoreCase("quoted-printable"))
			return new QuotedPrintableEncoder(QuotedPrintableEncoding.DEFAULT);
		else if (contentTransferEncoding.equalsIgnoreCase("base64"))
			return new Base64Encoder();
		else
			throw new IllegalArgumentException("No proper encoder found for " + contentTransferEncoding);
	}
	
	public static Transcoder<ByteBuffer> getDecoder(String contentTransferEncoding) {
		// according to http://www.w3.org/Protocols/rfc1341/5_Content-Transfer-Encoding.html, binary, 8bit and 7bit mean no encoding has been performed
		if (contentTransferEncoding == null || contentTransferEncoding.equalsIgnoreCase("7bit") || contentTransferEncoding.equalsIgnoreCase("8bit") || contentTransferEncoding.equalsIgnoreCase("binary"))
			return null;
		else if (contentTransferEncoding.equalsIgnoreCase("quoted-printable"))
			return new QuotedPrintableDecoder(QuotedPrintableEncoding.DEFAULT);
		else if (contentTransferEncoding.equalsIgnoreCase("base64"))
			return new Base64Decoder();
		else
			throw new IllegalArgumentException("Can not find a proper decoder for " + contentTransferEncoding);
	}
	
	public static boolean isInline(Header...headers) {
		String disposition = getHeaderAsValues("Content-Disposition", headers).get("value");
		return (disposition != null && disposition.equalsIgnoreCase("inline")) || getContentType(headers).startsWith("text/"); 
	}
	
	public static String getCharset(Header...headers) {
		String charset = getHeaderAsValues("Content-Type", headers).get("charset");
		// the default charset as defined by RFC822 only applies to text/* types
		return charset == null && getContentType(headers).startsWith("text/") ? "us-ascii" : charset;
	}
	
	public static Long getSize(Header...headers) {
		Header header = getHeader("Content-Length", headers);
		if (header != null)
			return new Long(header.getValue().trim());
		// in theory the content-disposition can also contain a "size" param
		String size = getHeaderAsValues("Content-Disposition", headers).get("size");
		return size != null ? new Long(size) : null;
	}

	public static Header [] readHeaders(ReadableContainer<CharBuffer> data) throws ParseException, IOException {
		return readHeaders(data, false);
	}
	
	public static Header [] readHeaders(ReadableContainer<CharBuffer> data, boolean mustFinish) throws ParseException, IOException {
		char [] singleChar = new char[1];
		char [] space = new char [] { ' ' };
		List<Header> headers = new ArrayList<Header>();
		char previousChar = 0;
		// amount of linebreaks (2 = end of headers)
		int lineBreaks = 0;
		// temporarily keep the header here
		CharBuffer header = IOUtils.newCharBuffer();
		boolean unfolding = false;
		// stop when two linebreaks are reached (end of headers)
		while(lineBreaks < 2 && data.read(IOUtils.wrap(singleChar, false)) == 1) {
			char currentChar = singleChar[0];
			// you can "fold" a long header by adding a linefeed followed by at least one whitespace
			if (previousChar == '\n' && (currentChar == ' ' || currentChar == '\t')) {
				unfolding = true;
				// write a single space to the header
				header.write(space);
			}
			// we can skip any space when unfolding (TODO: may need to be any whitespace)
			else if (unfolding && (currentChar == ' ' || currentChar == '\t'))
				continue;
			// line ending, this "should" be preceeded by a \r but this is not explicitly checked so incorrect line endings are allowed
			else if (currentChar == '\n')
				lineBreaks++;
			else if (currentChar == '\r')
				continue;
			else {
				// reset
				lineBreaks = 0;
				unfolding = false;
				
				// the linefeed was not used to indicate folding, we need to parse the content we have up till now as a header
				if (previousChar == '\n' && header.remainingData() > 0)
					headers.add(MimeHeader.parseHeader(IOUtils.toString(header)));
				header.write(singleChar);
			}
			previousChar = currentChar;
		}
		if (header.remainingData() > 0)
			headers.add(MimeHeader.parseHeader(IOUtils.toString(header)));
		// if the mustFinish boolean is toggled, we need the two ending linefeeds to indicate the end of the headers
		return mustFinish && lineBreaks < 2 ? null : headers.toArray(new Header[headers.size()]);
	}
	
	public static String getContentRange(Header...headers) {
		// the user can request a range, for example requesting the first 500 bytes would be (both inclusive):
		// Range: 0-499
		// The server can then respond with the header (assuming the total size is 1050 bytes):
		// Content-Range: 0-499/1050
		// note that the final request for the above used size example is:
		// Range: 1000-1049
		Header header = getHeader("Content-Range", headers);
		return header == null ? null : header.getValue();
	}
	
	public static String getFullHeaderValue(Header header) {
		StringBuilder builder = new StringBuilder();
		if (header.getValue() != null) {
			builder.append(header.getValue());
		}
		if (header.getComments() != null) {
			for (String comment : header.getComments()) {
				builder.append(";").append(comment);
			}
		}
		return builder.toString();
	}
	
	public static String format(Header header, boolean allowFolding, HeaderEncoding encoding) {
		try {
			if (header.getValue() == null) {
				throw new IllegalArgumentException("The header '" + header.getName() + "' has no value");
			}
			StringBuilder builder = new StringBuilder();
			builder.append(header.getName())
				.append(": ")
				.append(encoding != null ? encode(header.getValue(), Charset.defaultCharset(), encoding) : header.getValue());
			if (header.getComments() != null) {
				for (String comment : header.getComments()) {
					builder.append(";"); 
					if (allowFolding) {
						builder.append("\r\n\t");
					}
					builder.append(encoding != null ? encode(comment, Charset.defaultCharset(), encoding) : comment);
				}
			}
			return builder.toString();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static String encode(String value, Charset charset, HeaderEncoding encoding) throws IOException {
		switch(encoding) {
			case RFC2231: return MimeHeader.encodeRFC2231(value, charset);
			default: return MimeHeader.encode(value, charset);
		}
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
}
