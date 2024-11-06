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
import java.security.GeneralSecurityException;
import java.security.cert.CertPath;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import static be.nabu.utils.io.IOUtils.*;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.mime.api.Part;
import be.nabu.utils.security.BCSecurityUtils;
import be.nabu.utils.security.KeyStoreHandler;

public class ParsedSignedMimePart extends ParsedMimeBinaryPart implements ParseablePart {

	private boolean validated = false;
	private CertPath certPath;
	
	@Override
	public void parse() throws ParseException {
		String smimeType = MimeUtils.getHeaderAsValues("Content-Type", getHeaders()).get("smime-type");
		if (smimeType != null && !smimeType.equals("signed-data"))
			throw new ParseException("This part only supports the smime-type=signed-data", 0);
		if (!MimeUtils.getContentType(getParent().getHeaders()).equalsIgnoreCase("multipart/signed"))
			throw new ParseException("The parent part must be of type multipart/signed", 0);
	}
	
	public ParsedMimePart getSignedPart() {
		ParsedMimePart lastPart = null;
		for (Part child : getParent()) {
			ParsedMimePart parsed = (ParsedMimePart) child;
			if (parsed.equals(this))
				break;
			else
				lastPart = parsed;
		}
		return lastPart;
	}
	
	public CertPath getCertPath() throws IOException, ParseException {
		return isValid() ? certPath : null;
	}
	
	public boolean isValid() throws IOException, ParseException {
		if (!validated) {
			// turns out although a multipart/signed _should_ have exactly two parts (body + signature)
			// in some cases it has three: some people add a lead-in part before the first boundary of the multipart/signed
			// this lead-in part has to be a text/plain (because it is before the first boundary)
			// we need to skip it and NOT use it in our signature check
			ParsedMimePart previousPart = null;
			// the amount of bytes to skip so we don't use the first part in our signature calculation
			long sizeToSkip = 0;
			for (Part child : getParent()) {
				ParsedMimePart parsed = (ParsedMimePart) child;
				// stop if we have reached the signature
				if (parsed.equals(this))
					break;
				// if we already have a previous part, it better be a text/plain lead-in
				// if so, we need to ignore it
				else if (previousPart != null) {
					if (!MimeUtils.getContentType(previousPart.getHeaders()).toLowerCase().equals("text/plain"))
						throw new ParseException("The multipart/signed contains more than the two required parts and the additional part is not of type 'text/plain' but instead of type " + MimeUtils.getContentType(previousPart.getHeaders()) + ", this is currently not supported as the code can't deduce which part is signed", 0);
					else
						sizeToSkip += previousPart.getSize();
				}
				previousPart = parsed;
			}
			ParsedMimePart unencapsulated = previousPart;
			ReadableContainer<ByteBuffer> unencapsulatedSignedData = unencapsulated.getRawContent();
			unencapsulatedSignedData = limitReadable(unencapsulatedSignedData, getParent().getSize() - getSize() - sizeToSkip);
			
			ReadableContainer<ByteBuffer> signatures = getContent();
			try {
				List<X509Certificate> certificates = new ArrayList<X509Certificate>(getParser().getKeyStore().getCertificates().values());
				X509Certificate [] array = certificates.toArray(new X509Certificate[certificates.size()]);
				certPath = BCSecurityUtils.verify(toInputStream(unencapsulatedSignedData), toBytes(signatures), BCSecurityUtils.createCertificateStore(array), array);
				validated = true;
			}
			catch (GeneralSecurityException e) {
				throw new RuntimeException(e);
			}
		}
		return certPath != null;
	}

}
