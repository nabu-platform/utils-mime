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
import java.text.ParseException;

import static be.nabu.utils.io.IOUtils.*;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.WritableContainer;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.api.MultiPart;
import be.nabu.utils.mime.api.Part;
import be.nabu.utils.mime.api.PartFormatter;
import be.nabu.utils.security.BCSecurityUtils;
import be.nabu.utils.security.SynchronousEncryptionAlgorithm;

/**
 * In theory this works perfectly with a non-multipart child however I seem to recall that an encrypted part MUST have a MIME-Version header
 * which in turn must be in a multipart
 */
public class FormattedEncryptedMimePart extends MimePartBase<MultiPart> implements FormattablePart {

	private Part child;
	private PartFormatter formatter;
	private SynchronousEncryptionAlgorithm algorithm;
	private X509Certificate [] recipients;
	
	public FormattedEncryptedMimePart(Part multipart, X509Certificate...recipients) {
		this.child = multipart;
		this.recipients = recipients;
	}
	
	@Override
	public void format(WritableContainer<ByteBuffer> output) throws IOException, FormatException {
		try {
			Header [] headers = new Header [] {
				MimeHeader.parseHeader("MIME-Version: 1.0"),
				MimeHeader.parseHeader("Content-Type: application/pkcs7-mime; name=\"smime.p7m\"; smime-type=enveloped-data"),
			};
			MimeUtils.writeHeaders(output, headers);
			if (MimeUtils.getHeader("Content-Disposition", getHeaders()) == null)
				MimeUtils.writeHeaders(output, MimeHeader.parseHeader("Content-Disposition: attachment; filename=\"smime.p7m\""));
			if (MimeUtils.getHeader("Content-Transfer-Encoding", getHeaders()) == null)
				MimeUtils.writeHeaders(output, MimeHeader.parseHeader("Content-Transfer-Encoding: base64"));

			for (Header header : getHeaders()) {
				if (!header.getName().equalsIgnoreCase("MIME-Version") && !header.getName().equalsIgnoreCase("Content-Type"))
					MimeUtils.writeHeaders(output, header);
			}
			MimeFormatter.finishHeaders(output);
			
			WritableContainer<ByteBuffer> encoded = formatter.getTranscoder().encodeTransfer("base64", output);
			WritableContainer<ByteBuffer> encrypted = wrap(BCSecurityUtils.encrypt(toOutputStream(new NonPropagatingClose(encoded)), getAlgorithm(), recipients));
			formatter.format(child, encrypted);
			// need to close it to write end
			encrypted.close();
			// make sure it is all written to the output
			encoded.flush();
			output.write(wrap("\r\n".getBytes("ASCII"), true));
		}
		catch (GeneralSecurityException e) {
			throw new FormatException(e);
		}
		catch (ParseException e) {
			throw new FormatException(e);
		}		
	}

	public SynchronousEncryptionAlgorithm getAlgorithm() {
		if (algorithm == null)
			algorithm = SynchronousEncryptionAlgorithm.AES128_CBC;
		return algorithm;
	}

	public void setAlgorithm(SynchronousEncryptionAlgorithm algorithm) {
		this.algorithm = algorithm;
	}

	@Override
	public void setFormatter(PartFormatter formatter) {
		this.formatter = formatter;
	}

}
