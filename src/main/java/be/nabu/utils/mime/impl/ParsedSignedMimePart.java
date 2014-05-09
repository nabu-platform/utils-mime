package be.nabu.utils.mime.impl;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import static be.nabu.utils.io.IOUtils.*;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.security.BCSecurityUtils;
import be.nabu.utils.security.KeyStoreHandler;

public class ParsedSignedMimePart extends ParsedMimeBinaryPart implements ParseablePart {

	@Override
	public void parse() throws ParseException {
		String smimeType = MimeUtils.getHeaderAsValues("Content-Type", getHeaders()).get("smime-type");
		if (smimeType != null && !smimeType.equals("signed-data"))
			throw new ParseException("This part only supports the smime-type=signed-data", 0);
		if (!MimeUtils.getContentType(getParent().getHeaders()).equalsIgnoreCase("multipart/signed"))
			throw new ParseException("The parent part must be of type multipart/signed", 0);
	}
	
	public boolean isValid() throws IOException {
		ParsedMimePart unencapsulated = (ParsedMimePart) getParent().iterator().next();
		ReadableContainer<ByteBuffer> unencapsulatedSignedData = unencapsulated.getRawContent();
		unencapsulatedSignedData = limitReadable(unencapsulatedSignedData, getParent().getSize() - getSize());
		
		ReadableContainer<ByteBuffer> signatures = getContent();
		try {
			List<X509Certificate> certificates = new ArrayList<X509Certificate>(new KeyStoreHandler(getParser().getKeyStore().getKeyStore()).getCertificates().values());
			X509Certificate [] array = certificates.toArray(new X509Certificate[certificates.size()]);
			return BCSecurityUtils.verify(toInputStream(unencapsulatedSignedData), toBytes(signatures), BCSecurityUtils.createCertificateStore(array), array) != null;
		}
		catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

}
