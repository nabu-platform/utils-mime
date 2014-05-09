package be.nabu.utils.mime.impl;

import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.mime.api.ContentPart;

public class FormattedSignatureMimePart extends MimePartBase<FormattedSignedMimeMultiPart> implements ContentPart {

	private FormattedSignedMimePart signedPart;
	
	FormattedSignatureMimePart(FormattedSignedMimePart signedPart) {
		this.signedPart = signedPart;
		setHeader(
			new MimeHeader("Content-Type", "application/pkcs7-signature", "name=\"smime.p7s\""),
			new MimeHeader("Content-Disposition", "attachment", "filename=\"smime.p7s\"")
		);
	}
	
	@Override
	public ReadableContainer<ByteBuffer> getReadable() {
		return signedPart.getSignatures();
	}

}
