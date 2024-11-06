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
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.text.ParseException;

import static be.nabu.utils.io.IOUtils.*;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.security.BCSecurityUtils;

public class ParsedPKCS7MimeMultiPart extends ParsedMimeMultiPart implements ParseablePart {

	private String smimeType;
	
	@Override
	public void parse() throws ParseException, IOException {
		smimeType = MimeUtils.getHeaderAsValues("Content-Type", getHeaders()).get("smime-type");
		ReadableContainer<ByteBuffer> readable = getContent();
		try {
			int partNumber = 0;
			ParsedMimePart child = getParser().parse(countReadable(wrapReadable(readable, Charset.forName("ASCII"))), this, partNumber++);
			addParts(child);
		}
		finally {
			readable.close();
		}
	}
	
	@Override
	ReadableContainer<ByteBuffer> getContent() throws IOException {
		return getRawContent();
	}

	@Override
	ReadableContainer<ByteBuffer> getRawContent() throws IOException {
		ReadableContainer<ByteBuffer> readable = null;
		if (getParent() == null) {
			// get the data
			readable = getResource().getReadable();
			// skip to the correct position
			readable.read(newByteSink(getAbsoluteOffset()));
		}
		else {
			readable = getParent().getRawContent();
			readable.read(newByteSink(getRelativeOffset()));
		}
		readable = bufferReadable(getSize() > 0 ? limitReadable(readable, getSize()) : readable, newByteBuffer(1024*10, true));
		// we need to skip the headers
		readable.read(newByteSink(getBodyOffset()));
		// we need to decode if necessary
		readable = getParser().getTranscoder().decodeTransfer(MimeUtils.getTransferEncoding(getHeaders()), readable);
		readable = getParser().getTranscoder().decodeTransfer(MimeUtils.getContentTransferEncoding(getHeaders()), readable);
		readable = getParser().getTranscoder().decodeContent(MimeUtils.getContentEncoding(getHeaders()), readable);
		try {
			if (smimeType.equals("enveloped-data"))
				readable = wrap(BCSecurityUtils.decrypt(toInputStream(readable), getParser().getKeyStore()));
			else if (smimeType.equals("compressed-data"))
				readable = wrap(BCSecurityUtils.decompress(toInputStream(readable)));
		}
		catch (GeneralSecurityException e) {
			throw new IOException(e);
		}
		// return a limited view if necessary
		return readable;
	}
	
}
