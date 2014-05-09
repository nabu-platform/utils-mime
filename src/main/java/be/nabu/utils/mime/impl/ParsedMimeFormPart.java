package be.nabu.utils.mime.impl;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.List;
import java.util.Map;

import be.nabu.libs.resources.URIUtils;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.CharBuffer;
import be.nabu.utils.io.api.ReadableContainer;

/**
 * Content-Disposition is described in RFC2183
 * Content-Type is described in RFC2045
 */
public class ParsedMimeFormPart extends ParsedMimeBinaryPart implements ParseablePart {

	private Map<String, List<String>> values;

	public Map<String, List<String>> getValues() throws ParseException {
		return values;
	}

	@Override
	public void parse() throws ParseException, IOException {
		ReadableContainer<ByteBuffer> content = getContent();
		try {
			ReadableContainer<CharBuffer> charContent = IOUtils.wrapReadable(content, Charset.forName("ASCII"));
			String stringContent = IOUtils.toString(charContent);
			URI uri = new URI("/?" + URIUtils.URLEncodingToURIEncoding(stringContent));
			values = URIUtils.getQueryProperties(uri);
		}
		catch (URISyntaxException e) {
			throw new ParseException(e.getMessage(), 0);
		}
		finally {
			content.close();
		}
	}
	
}
