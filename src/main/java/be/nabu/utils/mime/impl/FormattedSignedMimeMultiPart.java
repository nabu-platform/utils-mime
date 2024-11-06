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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import be.nabu.utils.mime.api.MultiPart;
import be.nabu.utils.mime.api.Part;
import be.nabu.utils.security.SignatureType;
import be.nabu.utils.security.api.ManagedKeyStore;

public class FormattedSignedMimeMultiPart extends MimePartBase<MultiPart> implements MultiPart {

	private SignatureType signatureType;
	private ManagedKeyStore keyStore;
	private String [] aliases;
	private boolean includeChains = true;
	private Map<String, Part> children = new HashMap<String, Part>();
	
	/**
	 * You can sign the content part with multiple aliases
	 * Each alias must point to a private key
	 */
	public FormattedSignedMimeMultiPart(Part partToSign, SignatureType signatureType, ManagedKeyStore keyStore, String...aliases) {
		this.signatureType = signatureType;
		this.keyStore = keyStore;
		this.aliases = aliases;
		
		int partNumber = 0;
		FormattedSignedMimePart signedPart = new FormattedSignedMimePart(partToSign);
		signedPart.setParent(this, partNumber++);
		
		FormattedSignatureMimePart signaturePart = new FormattedSignatureMimePart(signedPart);
		signaturePart.setParent(this, partNumber++);
		
		children.put(signedPart.getName(), signedPart);
		children.put(signaturePart.getName(), signaturePart);
		
		setHeader(
			new MimeHeader("Content-Type", "multipart/signed", "protocol=\"application/pkcs7-signature\"")
		);
	}

	@Override
	public Part getChild(String name) {
		return children.get(name);
	}

	@Override
	public Iterator<Part> iterator() {
		return children.values().iterator();
	}
	
	public SignatureType getSignatureType() {
		return signatureType;
	}

	public ManagedKeyStore getKeyStore() {
		return keyStore;
	}

	public String[] getAliases() {
		return aliases;
	}

	public boolean isIncludeChains() {
		return includeChains;
	}

}
