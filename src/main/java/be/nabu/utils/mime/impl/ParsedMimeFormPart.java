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
