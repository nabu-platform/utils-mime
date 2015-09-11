package be.nabu.utils.mime.impl;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyPair;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

import junit.framework.TestCase;
import be.nabu.libs.resources.ResourceFactory;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.api.WritableContainer;
import be.nabu.utils.mime.api.MultiPart;
import be.nabu.utils.mime.api.Part;
import be.nabu.utils.security.BCSecurityUtils;
import be.nabu.utils.security.KeyPairType;
import be.nabu.utils.security.SecurityUtils;
import be.nabu.utils.security.SignatureType;
import be.nabu.utils.security.api.ManagedKeyStore;
import be.nabu.utils.security.impl.SimpleManagedKeyStore;

public class TestMimeFormatter extends TestCase {
	
	public void testExample1() throws URISyntaxException, ParseException, IOException, FormatException {
		assertEquals(
			new URI("classpath:/example.mime"), 
			new URI("classpath:/formatted.example.mime")
		);
	}
	
	public void testExample2() throws URISyntaxException, ParseException, IOException, FormatException {
		assertEquals(
			new URI("classpath:/example2.mime"), 
			new URI("classpath:/formatted.example2.mime")
		);
	}
	
	public void testPlainPost() throws URISyntaxException, ParseException, IOException, FormatException {
		assertEquals(
			new URI("classpath:/plainpost.html"), 
			new URI("classpath:/formatted.plainpost.html")
		);
	}
	
	public void testFormUpload() throws URISyntaxException, ParseException, IOException, FormatException {
		assertEquals(
			new URI("classpath:/formupload.html"), 
			new URI("classpath:/formatted.formupload.html")
		);
	}
	
	public static ReadableResource getResource(URI uri) throws IOException {
		return (ReadableResource) ResourceFactory.getInstance().resolve(uri, null);
	}
	
	public void testCompress() throws ParseException, URISyntaxException, IOException, FormatException {
		testCompress(new MimeFormatter());
		testCompress(new PullableMimeFormatter());
	}

	private void testCompress(MimeFormatter formatter) throws ParseException, IOException, URISyntaxException, FormatException {
		Part example = new MimeParser().parse(getResource(new URI("classpath:/example.mime")));
		
		Part compressed = MimeUtils.compress(example);
		URI target = new URI("memory:/test/mime/compressed.mime");
		Resource resource = ResourceUtils.touch(target, null);
		WritableContainer<ByteBuffer> output = ((WritableResource) resource).getWritable();
		formatter.format(compressed, output);
		output.close();
		
		assertEquals(toString(new URI("classpath:/compressed.example.mime")).replace("\r", ""), toString(target).replace("\r", ""));
		
		MimeParser parser = new MimeParser();
		MultiPart parsed = (MultiPart) parser.parse(getResource(target));
		Part first = parsed.getChild("part0");
		
		ByteBuffer decompressed = IOUtils.newByteBuffer();
		formatter.format(first, decompressed);
		IOUtils.close(decompressed);
		assertEquals(toString(new URI("classpath:/formatted.example.mime")).replace("\r", ""), new String(IOUtils.toBytes(decompressed)).replace("\r", ""));
	}
	
	public void testEncrypted() throws NoSuchAlgorithmException, CertificateException, IOException, URISyntaxException, ParseException, KeyStoreException, FormatException {
		testEncrypted(new MimeFormatter());
		testEncrypted(new PullableMimeFormatter());
	}

	private void testEncrypted(MimeFormatter formatter) throws NoSuchAlgorithmException, CertificateException, IOException, ParseException, URISyntaxException, FormatException, KeyStoreException {
		X500Principal principal = SecurityUtils.createX500Principal("test", "nabu", null, null, null, "Belgium");
		KeyPair pair = SecurityUtils.generateKeyPair(KeyPairType.RSA, 2048);
		X509Certificate certificate = BCSecurityUtils.generateSelfSignedCertificate(pair, new Date(new Date().getTime() + 1000*60*60*24*365), principal, principal);
		
		Part example = new MimeParser().parse(getResource(new URI("classpath:/example.mime")));
		
		Part encrypted = MimeUtils.encrypt((MultiPart) example, certificate);
		
		URI target = new URI("memory:/test/mime/encrypted.mime");
		Resource resource = ResourceUtils.touch(target, null);
		WritableContainer<ByteBuffer> output = ((WritableResource) resource).getWritable();
		formatter.format(encrypted, output);
		output.close();
		
		ManagedKeyStore keyStore = new SimpleManagedKeyStore();
		keyStore.set("priv", pair.getPrivate(), new X509Certificate [] { certificate }, null);
		MimeParser parser = new MimeParser();
		parser.setKeyStore(keyStore);
		Part parsed = parser.parse(getResource(target));
		Part childPart = (Part) (((MultiPart) parsed).getChild("part0"));
		
		ByteBuffer decrypted = IOUtils.newByteBuffer();
		formatter.format(childPart, decrypted);
		decrypted.close();
		assertEquals(toString(new URI("classpath:/formatted.example.mime")).replace("\r", ""), new String(IOUtils.toBytes(decrypted)).replace("\r", ""));
	}
	
	public void assertEquals(URI original, URI formatted) throws ParseException, IOException, FormatException {
		assertEquals(original, formatted, new MimeFormatter());
		assertEquals(original, formatted, new PullableMimeFormatter());
	}

	private void assertEquals(URI original, URI formatted, MimeFormatter formatter) throws IOException, FormatException, ParseException {
		MimeParser parser = new MimeParser();
		
		ByteBuffer output = IOUtils.newByteBuffer();
		formatter.format(parser.parse(getResource(original)), output);
		IOUtils.close(output);
		assertEquals(toString(formatted).replace("\r", ""), new String(IOUtils.toBytes(output)).replace("\r", ""));
	}
	
	public static String toString(URI uri) throws IOException {
		ReadableResource resource = (ReadableResource) ResourceFactory.getInstance().resolve(uri, null);
		ReadableContainer<ByteBuffer> readable = resource.getReadable();
		try {
			return new String(IOUtils.toBytes(readable));
		}
		finally {
			readable.close();
		}
	}

	public void testSign() throws URISyntaxException, ParseException, KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException, FormatException {
		X500Principal principal = SecurityUtils.createX500Principal("test", "nabu", null, null, null, "Belgium");
		KeyPair pair = SecurityUtils.generateKeyPair(KeyPairType.RSA, 2048);
		X509Certificate certificate = BCSecurityUtils.generateSelfSignedCertificate(pair, new Date(new Date().getTime() + 1000*60*60*24*365), principal, principal);
		ManagedKeyStore keyStore = new SimpleManagedKeyStore();
		keyStore.set("priv", pair.getPrivate(), new X509Certificate [] { certificate }, null);
		keyStore.set("trusted", certificate);
		testSign(new MimeFormatter(), keyStore);
		testSign(new PullableMimeFormatter(), keyStore);
	}

	private void testSign(MimeFormatter formatter, ManagedKeyStore keyStore) throws URISyntaxException, ParseException, KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException, FormatException {
		URI contentUri = new URI("classpath:/example.mime");
		Part part = new MimeParser().parse(getResource(contentUri));
		
		MultiPart signedPart = MimeUtils.sign(part, SignatureType.SHA256WITHRSA, keyStore, "priv");
		
		URI target = new URI("memory:/test/mime/signed.mime");
		Resource resource = ResourceUtils.touch(target, null);
		WritableContainer<ByteBuffer> output = ((WritableResource) resource).getWritable();
		formatter.format(signedPart, output);
		output.close();
		
		MimeParser parser = new MimeParser();
		parser.setKeyStore(keyStore);
		MultiPart parsedPart = (MultiPart) parser.parse(getResource(target));
		ParsedSignedMimePart signature = (ParsedSignedMimePart) parsedPart.getChild("smime.p7s");
		assertTrue(signature.isValid());
	}
}
