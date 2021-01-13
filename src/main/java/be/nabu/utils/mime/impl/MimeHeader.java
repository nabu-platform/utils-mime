package be.nabu.utils.mime.impl;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.annotation.XmlTransient;

import be.nabu.utils.codec.TranscoderUtils;
import be.nabu.utils.codec.api.Transcoder;
import be.nabu.utils.codec.impl.Base64Decoder;
import be.nabu.utils.codec.impl.Base64Encoder;
import be.nabu.utils.codec.impl.QuotedPrintableDecoder;
import be.nabu.utils.codec.impl.QuotedPrintableEncoder;
import be.nabu.utils.codec.impl.QuotedPrintableEncoding;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.api.ModifiableHeader;

/**
 * TODO: Still need to add folding 
 */
public class MimeHeader implements ModifiableHeader {

	private String name, value;
	private List<String> comments;
	private Charset charset = Charset.defaultCharset();
	// defaults to tab for readability but you may want to change this in some settings
	private char foldChar = '	';

	public static MimeHeader parseHeader(String headerData) throws ParseException, IOException {
		int separatorIndex = headerData.indexOf(":");
		if (separatorIndex < 0)
			throw new ParseException("The header does not contain a valid separator: " + headerData, 1);
		String name = headerData.substring(0, separatorIndex).trim();
		String [] parts = headerData.substring(separatorIndex + 1).split(";");
		String value = decode(parts[0].trim());
		String [] comments = new String[parts.length - 1];
		for (int i = 1; i < parts.length; i++)
			comments[i - 1] = decode(parts[i].trim());
		return new MimeHeader(name, value, comments);
	}
	
	private static String decode(String headerData) throws ParseException, IOException {
		// an encoded value can be split over multiple lines when folding
		// the folded linefeeds have already been removed, but the encoded bit was also split into two, make it one again
		headerData = headerData.replaceAll("\\?==\\?[^?]+\\?[^?]+\\?", "");
		Pattern pattern = Pattern.compile("[\\s]*=\\?([^?]+)\\?([^?]+)\\?([^?]+)\\?=[\\s]*");
		Matcher matcher = pattern.matcher(headerData);
		try {
			while (matcher.find()) {
				String encoding = pattern.matcher(matcher.group()).replaceAll("$1");
				String type = pattern.matcher(matcher.group()).replaceAll("$2");
				String encoded = pattern.matcher(matcher.group()).replaceAll("$3");
				String decoded = null;
				if (type.equalsIgnoreCase("B")) {
					decoded = IOUtils.toString(IOUtils.wrapReadable(
						TranscoderUtils.transcodeBytes(IOUtils.wrap(encoded.getBytes("ASCII"), true), new Base64Decoder()),
						Charset.forName(encoding))
					);
				}
				else if (type.equalsIgnoreCase("Q")) {
					decoded = IOUtils.toString(IOUtils.wrapReadable(
						TranscoderUtils.transcodeBytes(IOUtils.wrap(encoded.getBytes("ASCII"), true), new QuotedPrintableDecoder(QuotedPrintableEncoding.WORD)),
						Charset.forName(encoding))
					);
				}
				else
					throw new ParseException("The header uses encoding '" + type + "' which is currently not supported", 0);
				headerData = headerData.replace(matcher.group(), decoded);
			}
			return headerData;
		}
		catch (UnsupportedEncodingException e) {
			throw new ParseException(e.getMessage(), 0);
		}
	}
	
	public enum EncodingMatchRate {
		MOSTLY_UNALLOWED,
		MOSTLY_ALLOWED,
		FULLY_ALLOWED
	}
	
	private static EncodingMatchRate getEncodingMatchRate(String string) {
		return getEncodingMatchRate(string, "", false);
	}
	private static EncodingMatchRate getEncodingMatchRate(String string, String specialCharacters, boolean encodeSpaces) {
		int match = 0;
		for (int i = 0; i < string.length(); i++) {
			int character = string.codePointAt(i);
			boolean isSpecialCharacter = specialCharacters.indexOf(character) >= 0;
			
			if (character >= 32 && character < 127 && !isSpecialCharacter && (character != ' ' || !encodeSpaces))
				match++;
		}
		if (match == string.length())
			return EncodingMatchRate.FULLY_ALLOWED;
		else if (match > string.length() * 0.5)
			return EncodingMatchRate.MOSTLY_ALLOWED;
		else
			return EncodingMatchRate.MOSTLY_UNALLOWED;
	}
	
	public MimeHeader() {
		// auto construct
	}
	
	public MimeHeader(String name, String value, String...comments) {
		this.name = name;
		this.value = value;
		this.comments = comments == null || comments.length == 0 ? new ArrayList<String>() : new ArrayList<String>(Arrays.asList(comments));
	}
	
	public String getName() {
		return name;
	}

	public String getValue() {
		return value;
	}
	
	public String [] getComments() {
		if (comments == null) {
			comments = new ArrayList<String>();
		}
		return comments.toArray(new String[comments.size()]);
	}
	
	public void setComments(String...comments) {
		this.comments = new ArrayList<String>();
		this.comments.addAll(Arrays.asList(comments));
	}
	
	@Override
	public void addComment(String...comments) {
		if (this.comments == null) {
			this.comments = new ArrayList<String>();
		}
		this.comments.addAll(Arrays.asList(comments));
	}

	@Override
	public String toString() {
		try {
			String header = getName() + ": " + encode(getValue(), getCharset());
			for (String comment : comments)
				header += ";\r\n" + getFoldChar() + encode(comment, getCharset());
			return header;
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private static Transcoder<ByteBuffer> getEncoder(String value) {
//		switch(getEncodingMatchRate(value, QuotedPrintableEncoding.WORD.getCharactersToEncode(), QuotedPrintableEncoding.WORD.isEncodeSpaces())) {
		switch(getEncodingMatchRate(value)) {
			// should use base64 encoding
			case MOSTLY_UNALLOWED: return new Base64Encoder();
			// can use quoted printable encoding
			case MOSTLY_ALLOWED: return new QuotedPrintableEncoder(QuotedPrintableEncoding.WORD);
			// no need for encoding
			default: return null;
		}
	}
	
	// based on RFC2047, chrome does not seem to like this when used for example for Content-Disposition
	public static String encode(String value, Charset charset) throws IOException {
		Transcoder<ByteBuffer> transcoder = getEncoder(value);
		if (transcoder == null)
			return value;
		else {
			return "=?" + charset.displayName() + "?" + (transcoder instanceof Base64Encoder ? "B" : "Q") + "?" + IOUtils.toString(IOUtils.wrapReadable(
				TranscoderUtils.transcodeBytes(IOUtils.wrap(value.getBytes(charset), true), transcoder), 
				Charset.forName("ASCII")
			)) + "?=";
		}
	}
	
	// it seems if we construct a header like this
	// Header header = new MimeHeader("Content-Disposition", "attachment;fileName=\"tést.pdf\"");
	// the mime header will not parse further and the full thing is considered the value rather than being split off into comments
	// this seems to be the "standard" way it is used throughout the codebase, meaning we generally get full value and no comments
	// this method is based on that assumption...
	// note that we do not support part 3 of the spec yet -> encoding of wrapped headers...
	public static String encodeRFC2231(String value, Charset charset) throws IOException {
		boolean first = true;
		StringBuilder builder = new StringBuilder();
		for (String part : value.split(";")) {
			Transcoder<ByteBuffer> transcoder = getEncoder(part);
			// if the first part is already not OK, encode it fully using the older encoding (this should not occur for HTTP?)
			if (transcoder != null && first) {
				builder.append(encode(value, charset));
				break;
			}
			else {
				if (!first) {
					builder.append(";");
				}
				if (transcoder != null) {
					// if we have a key/value pair, we can encode it with this RFC
					int indexOf = part.indexOf('=');
					if (indexOf >= 0) {
						String key = part.substring(0, indexOf);
						Transcoder<ByteBuffer> keyEncoder = getEncoder(key);
						// if the key itself needs encoding as well, we can't help you
						if (keyEncoder != null) {
							builder.append(encode(part, charset));
						}
						else {
							// the original spec is not clear about for example content-disposition headers where quotes can be used around the value of a comment as well
							// but testing encoded like this: attachment;filename*=UTF-8''%22t%C3%A9st.pdf%22
							// where the quotes are encoded as %22 before the tést that triggered it
							// this ends up being downloaded as _tést.pdf_ where the quotes are changed to underscores
							// RFC5987 article 3.2.2 (examples) does list some examples with no quotes at all, with quotes and with full encoding
							// it seems to be an or situation rather than an and, so if we find quotes, we remove them when we are encoding
							String toEncode = part.substring(indexOf + 1);
							if (toEncode.startsWith("\"") && toEncode.endsWith("\"")) {
								toEncode = toEncode.substring(1, toEncode.length() - 1);
							}
							// as per the spec, we can leave the "language" empty but we do need to add the empty ' to indicate where it ends
							builder.append(key).append("*=").append(charset.name()).append("''").append(URLEncoder.encode(toEncode, charset.name()).replace("+", "%20"));
						}
					}
					// no comment? do it all
					else {
						builder.append(encode(part, charset));
					}
				}
				else {
					builder.append(part);
				}
			}
			first = false;
		}
		return builder.toString();
	}

	public Charset getCharset() {
		return charset;
	}

	public void setCharset(Charset charset) {
		this.charset = charset;
	}

	public Character getFoldChar() {
		return foldChar;
	}

	public void setFoldChar(Character foldChar) {
		this.foldChar = foldChar == null ? '	' : foldChar;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public void setValue(String value) {
		this.value = value;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((comments == null) ? 0 : comments.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((value == null) ? 0 : value.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		else if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		MimeHeader other = (MimeHeader) obj;
		if (comments == null) {
			if (other.comments != null) {
				return false;
			}
		}
		else if (!comments.equals(other.comments)) {
			return false;
		}
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		}
		else if (!name.equals(other.name)) {
			return false;
		}
		if (value == null) {
			if (other.value != null) {
				return false;
			}
		}
		else if (!value.equals(other.value)) {
			return false;
		}
		return true;
	}

	@XmlTransient
	public String getContent() {
		return MimeUtils.getFullHeaderValue(this);
	}
}
