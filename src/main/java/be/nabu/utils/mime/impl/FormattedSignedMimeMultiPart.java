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
