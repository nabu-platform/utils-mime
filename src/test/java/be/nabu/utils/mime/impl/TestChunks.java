package be.nabu.utils.mime.impl;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;

import junit.framework.TestCase;
import be.nabu.libs.resources.ResourceFactory;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.Container;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.api.Part;

public class TestChunks extends TestCase {
	
	public void testChunkParsing() throws URISyntaxException, ParseException, IOException {
		URI uri = new URI("classpath:/chunked.html");
		MimeParser parser = new MimeParser();
		Part part = parser.parse(TestMimeParser.getResource(uri));
		assertEquals("abcdefghijklmnopqrstuvwxyz1234567890abcdef", TestMimeParser.toString(part));
		Header header = MimeUtils.getHeader("Some-Footer", part.getHeaders());
		assertNotNull(header);
		assertEquals("some-value", header.getValue());
		header = MimeUtils.getHeader("Another-Footer", part.getHeaders());
		assertNotNull(header);
		assertEquals("another-value", header.getValue());
	}
	
	/**
	 * Fun fact: if you check the result in the following you will see chunks that are apparently surrounded by double linefeeds
	 * However this is not entirely true, what is happening is that the chunk border is right between a CRLF of the quoted encoding
	 * It is actually this in the backend: \r\r\n200\r\n\n
	 * where the first \r and last \n belong together
	 * This is auto-corrected when you try to copy paste the result from say a console so fun is guaranteed!
	 * @throws IOException 
	 * @throws FormatException 
	 */
	public void testChunkFormatting() throws URISyntaxException, ParseException, IOException, FormatException {
		ParsedMimePart part = new MimeParser().parse((ReadableResource) ResourceFactory.getInstance().resolve(new URI("classpath:/wikipedia.gz"), null));

		PlainMimePart newPart = new PlainMimeContentPart(null, ((ReadableResource) part).getReadable());
		newPart.setHeader(new MimeHeader("Transfer-Encoding", "chunked"));
		
		Container<ByteBuffer> container = ResourceUtils.toContainer(new URI("memory:/test/mime/chunked.mime"), null);
		try {
			MimeFormatter formatter = new MimeFormatter();
			formatter.setChunkSize(512);
			formatter.format(newPart, container);
			container.flush();
			
			String expected = TestMimeFormatter.toString(new URI("classpath:/chunked.wikipedia.mime"));
			String actual = new String(IOUtils.toBytes(container));
			assertEquals(expected.replace("\r", ""), actual.replace("\r", ""));
		}
		finally {
			container.close();
		}
	}
	
}
