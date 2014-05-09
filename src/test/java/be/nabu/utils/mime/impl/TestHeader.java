package be.nabu.utils.mime.impl;

import java.io.IOException;
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
