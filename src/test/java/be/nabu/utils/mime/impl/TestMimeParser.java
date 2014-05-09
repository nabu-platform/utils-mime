package be.nabu.utils.mime.impl;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.text.ParseException;

import junit.framework.TestCase;
import be.nabu.libs.resources.ResourceFactory;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.mime.api.MultiPart;
import be.nabu.utils.mime.api.Part;

public class TestMimeParser extends TestCase {

	public static ReadableResource getResource(URI uri) throws IOException {
		return (ReadableResource) ResourceFactory.getInstance().resolve(uri, null);
	}

	public void testExample1() throws URISyntaxException, ParseException, IOException {
		URI uri = new URI("classpath:/example.mime");
		MimeParser parser = new MimeParser();
		MultiPart part = (MultiPart) parser.parse(getResource(uri));
		assertEquals("all your subject are belong to us", MimeUtils.getHeader("Subject", part.getHeaders()).getValue());
		assertEquals("\"Misses Claus\" <misses@claus.np>", MimeUtils.getHeader("From", part.getHeaders()).getValue());
		
		MultiPart multipart = (MultiPart) part.getChild("part0");
		assertNotNull(multipart);
		Part ciceroPartOne = multipart.getChild("part0");
			assertNotNull(ciceroPartOne);
			assertEquals("Sed ut perspiciatis, unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam eaque ipsa, quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt, explicabo. Nemo enim ipsam voluptatem, quia voluptas sit, aspernatur aut odit aut fugit, sed quia consequuntur magni dolores eos, qui ratione voluptatem sequi nesciunt, neque porro quisquam est, qui dolorem ipsum, quia dolor sit amet consectetur adipisci[ng] velit, sed quia non numquam [do] eius modi tempora inci[di]dunt, ut labore et dolore magnam aliquam quaerat voluptatem. Ut enim ad minima veniam, quis nostrum exercitationem ullam corporis suscipit laboriosam, nisi ut aliquid ex ea commodi consequatur? Quis autem vel eum iure reprehenderit, qui in ea voluptate velit esse, quam nihil molestiae consequatur, vel illum, qui dolorem eum fugiat, quo voluptas nulla pariatur?",
				toString(ciceroPartOne));
		Part ciceroPartTwo = multipart.getChild("holy_crappers_its_cicero.txt");
			assertNotNull(ciceroPartTwo);
			assertEquals("At vero eos et accusamus et iusto odio dignissimos ducimus, qui blanditiis praesentium voluptatum deleniti atque corrupti, quos dolores et quas molestias excepturi sint, obcaecati cupiditate non provident, similique sunt in culpa, qui officia deserunt mollitia animi, id est laborum et dolorum fuga. Et harum quidem rerum facilis est et expedita distinctio. Nam libero tempore, cum soluta nobis est eligendi optio, cumque nihil impedit, quo minus id, quod maxime placeat, facere possimus, omnis voluptas assumenda est, omnis dolor repellendus. Temporibus autem quibusdam et aut officiis debitis aut rerum necessitatibus saepe eveniet, ut et voluptates repudiandae sint et molestiae non recusandae. Itaque earum rerum hic tenetur a sapiente delectus, ut aut reiciendis voluptatibus maiores alias consequatur aut perferendis doloribus asperiores repellat…",
				toString(ciceroPartTwo));
		Part xml = part.getChild("amigad a file.xml");
			assertNotNull(xml);
			assertEquals("<test>\n\t<this/>\n</test>", toString(xml));
	}

	// example pulled from http://msdn.microsoft.com/en-us/library/ms526560%28v=exchg.10%29.aspx
	public void testExample2() throws URISyntaxException, ParseException, IOException {
		URI uri = new URI("classpath:/example2.mime");
		MimeParser parser = new MimeParser();
		MultiPart part = (MultiPart) parser.parse(getResource(uri));
		Part leadingText = part.getChild("part0");
			assertNotNull(leadingText);
			assertEquals("This is a multipart message in MIME format.", toString(leadingText));
		Part bodyText = part.getChild("part1");
			assertNotNull(bodyText);
			assertEquals("this is the body text", toString(bodyText));
		Part attachmentText = part.getChild("test.txt");
			assertNotNull(attachmentText);
			assertEquals("this is the attachment text", toString(attachmentText));
			assertEquals("attachment", MimeUtils.getHeader("CONTENT-DISPOSITION", attachmentText.getHeaders()).getValue());
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

	// example pulled from http://docs.oracle.com/javaee/6/tutorial/doc/glraq.html
	public void testExamplePost() throws ParseException, URISyntaxException, IOException {
		URI uri = new URI("classpath:/formupload.html");
		MimeParser parser = new MimeParser();
		MultiPart part = (MultiPart) parser.parse(getResource(uri));
		Part file = part.getChild("sample.txt");
			assertEquals("file", MimeUtils.getFormName(file.getHeaders()));
			assertNotNull(file);
			assertEquals("Data from sample file", toString(file));
		Part destination = part.getChild("part1");
			assertEquals("destination", MimeUtils.getFormName(destination.getHeaders()));
			assertNotNull(destination);
			assertEquals("/tmp", toString(destination));
		Part upload = part.getChild("part2");
			assertEquals("upload", MimeUtils.getFormName(upload.getHeaders()));
			assertNotNull(upload);
			assertEquals("Upload", toString(upload));
	}
	
	// example pulled from http://www.htmlcodetutorial.com/forms/form_enctype.html
	public void testPlainPost() throws ParseException, URISyntaxException, IOException {
		URI uri = new URI("classpath:/plainpost.html");
		MimeParser parser = new MimeParser();
		ParsedMimeFormPart part = (ParsedMimeFormPart) parser.parse(getResource(uri));
			assertEquals("Steve Johnson", part.getValues().get("realname").get(0));
			assertEquals("steevo@idocs.com", part.getValues().get("email").get(0));
	}
	
	
}
