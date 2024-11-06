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
