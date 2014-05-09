package be.nabu.utils.mime.impl;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.Arrays;

import junit.framework.TestCase;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.Container;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.mime.api.Part;

public class TestGZIP extends TestCase {
	
	public void testGZIP() throws IOException, URISyntaxException, ParseException, FormatException {
		URI uri = new URI("classpath:/wikipedia.gz");
		
		MimeParser parser = new MimeParser();
		Part part = parser.parse(TestMimeFormatter.getResource(uri));
		
		assertEquals(toString(new URI("classpath:/wikipedia.html")), toString(part));
		
		MimeFormatter formatter = new MimeFormatter();
		formatter.setAllowBinary(true);
		Container<ByteBuffer> container = IOUtils.newByteBuffer();
		formatter.format(part, container);
		container.flush();
		
		assertTrue(Arrays.equals(toBytes(new URI("classpath:/wikipedia.gz")), IOUtils.toBytes(container)));
	}
		
	public static String toString(Part part) throws IOException {
		ReadableContainer<ByteBuffer> input = ((ReadableResource) part).getReadable();
		try {
			return IOUtils.toString(IOUtils.wrapReadable(input, Charset.forName("UTF-8")));
		}
		finally {
			input.close();
		}
	}
	
	public static String toString(URI uri) throws IOException {
		ReadableContainer<ByteBuffer> bytes = ResourceUtils.toReadableContainer(uri, null);
		try {
			return new String(IOUtils.toBytes(bytes), "UTF-8");
		}
		finally {
			bytes.close();
		}
	}
	
	public static byte [] toBytes(Part part) throws IOException {
		ReadableContainer<ByteBuffer> input = ((ReadableResource) part).getReadable();
		try {
			return IOUtils.toBytes(input);
		}
		finally {
			input.close();
		}
	}
	
	public static byte [] toBytes(URI uri) throws IOException {
		ReadableContainer<ByteBuffer> bytes = ResourceUtils.toReadableContainer(uri, null);
		try {
			return IOUtils.toBytes(bytes);
		}
		finally {
			bytes.close();
		}
	}
}
