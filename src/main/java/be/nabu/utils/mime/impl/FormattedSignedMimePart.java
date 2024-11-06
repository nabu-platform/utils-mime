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
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static be.nabu.utils.io.IOUtils.*;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.api.WritableContainer;
import be.nabu.utils.io.buffers.FragmentedReadableContainer;
import be.nabu.utils.io.buffers.bytes.DynamicByteBuffer;
import be.nabu.utils.io.buffers.bytes.StaticByteBuffer;
import be.nabu.utils.mime.api.Part;
import be.nabu.utils.mime.api.PartFormatter;
import be.nabu.utils.security.BCSecurityUtils;
import be.nabu.utils.security.BCSecurityUtils.SignedOutputStream;

public class FormattedSignedMimePart extends MimePartBase<FormattedSignedMimeMultiPart> implements FormattablePart {
	
	private DynamicByteBuffer signatures = new DynamicByteBuffer();
	
	private Part partToSign;
	
	private PartFormatter formatter;
	
	FormattedSignedMimePart(Part partToSign) {
		this.partToSign = partToSign;
	}
	
	public ReadableContainer<ByteBuffer> getSignatures() {
		FragmentedReadableContainer<ByteBuffer, StaticByteBuffer> duplicate = signatures.duplicate(true);
		duplicate.close();
		return duplicate;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void format(WritableContainer<ByteBuffer> output) throws FormatException, IOException {
		try {
			// remove any data in the signatures
			signatures.truncate();
			// mark it so we can reread signatures
			signatures.mark();
			List<X509Certificate> certificates = new ArrayList<X509Certificate>();
			if (getParent().isIncludeChains()) {
				// include the entire chains
				for (String alias : getParent().getAliases())
					certificates.addAll(Arrays.asList(getParent().getKeyStore().getChain(alias)));
			}
			// signatures are always detached in s/mime
			SignedOutputStream signedContainer = BCSecurityUtils.sign(toOutputStream(signatures), BCSecurityUtils.createSignerStore(
					BCSecurityUtils.createSignerStore(getParent().getSignatureType(), getParent().getKeyStore(), getParent().getAliases())
				), false, 
				BCSecurityUtils.createCertificateStore(certificates.toArray(new X509Certificate[certificates.size()]))
			);
			
			// combine the output you need to format to and the signature generating output
			WritableContainer<ByteBuffer> combinedOutput = multicast(output, wrap(signedContainer));

			// format the part to the output
			formatter.format(partToSign, combinedOutput);
			
			signedContainer.close();
			
			combinedOutput.write(wrap("\r\n".getBytes("ASCII"), true));
		}
		catch (GeneralSecurityException e) {
			throw new FormatException(e);
		}
	}

	@Override
	public void setFormatter(PartFormatter formatter) {
		this.formatter = formatter;
	}
}
