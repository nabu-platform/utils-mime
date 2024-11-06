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
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.Arrays;

import junit.framework.TestCase;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.impl.MimeHeader;

public class TestHeader extends TestCase {

	public void testBoundary() throws ParseException, IOException {
		String boundaryHeader = "Content-Type: multipart/signed; protocol=\"application/pkcs7-signature\"; micalg=sha1;\r\n"
				+ "	boundary=\"----=_Part_123456_1234567890.1234567890\"";
		Header header = MimeHeader.parseHeader(boundaryHeader);
		assertEquals("----=_Part_123456_1234567890.1234567890", MimeUtils.getHeaderAsValues("Content-Type", header).get("boundary"));
	}
	
	public void testSimpleParse() throws ParseException, IOException {
		assertHeader(
			MimeHeader.parseHeader("Content-Type: text/plain"),
			"Content-Type",
			"text/plain"
		);
	}
	
	public void testRfc2231() throws IOException {
		MimeHeader mimeHeader = new MimeHeader("Content-Disposition", "attachment; fileName=\"tést.pdf\"");
		String encodeRFC2231 = MimeHeader.encodeRFC2231(mimeHeader.getValue(), Charset.forName("UTF-8"));
		assertEquals("attachment; fileName*=UTF-8''t%C3%A9st.pdf", encodeRFC2231);
	}
	
	public void testQuotedPrintableParse() throws ParseException, IOException {
		String header = "Subject : =?iso-8859-1?Q?We=5Fneed_to_test-some-things=5Flike=5Fth=FCs=5Fto_find=2E?=\r\n"
				+ "=?iso-8859-1?Q?errors_?=";
		assertHeader(
			MimeHeader.parseHeader(header),
			"Subject",
			"We_need to test-some-things_like_thüs_to find.errors "
		);
		// the following should fail as the encoding is incorrect
		try {
			MimeHeader.parseHeader(header.replace("iso-8859-1", "UTF-8"));
			fail("The content should not be decodeable as it is not valid UTF-8");
		}
		catch(Exception e) {
			// expected
		}
	}
	
	public void assertHeader(Header header, String name, String value, String...comments) {
		assertEquals(name, header.getName());
		assertEquals(value, header.getValue());
		assertEquals(Arrays.asList(comments), Arrays.asList(header.getComments()));
	}
	
	public void testFormat() throws ParseException, IOException {
		String subject = "We_need to test-some-things_like_thüs_to find.errors ";
		Header header = new MimeHeader("Subject", subject);
		assertEquals("Subject: =?UTF-8?Q?We=5Fneed_to_test-some-things=5Flike=5Fth=C3=BCs=5Fto_find=2Eerrors_?=", header.toString());
	
		assertHeader(
			MimeHeader.parseHeader(header.toString()),
			"Subject",
			subject
		);
		
		// test non-encoded
		header = new MimeHeader("Content-Type", "text/plain");
		assertEquals("Content-Type: text/plain", header.toString());
		assertHeader(
			MimeHeader.parseHeader(header.toString()),
			"Content-Type",
			"text/plain"
		);
	}
}
