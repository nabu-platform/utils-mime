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

package be.nabu.utils.mime.util;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import static be.nabu.utils.io.IOUtils.*;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.WritableContainer;
import be.nabu.utils.mime.api.Header;

public class ChunkedWritableByteContainer implements WritableContainer<ByteBuffer> {

	private WritableContainer<ByteBuffer> parent;
	private ByteBuffer buffer = newByteBuffer();
	
	private boolean finished = false;
	
	/**
	 * The ending is two CRLFs, but in general the formatter will take care of this as the ending is the same for all parts
	 */
	private boolean writeEnding = true;
	
	public ChunkedWritableByteContainer(WritableContainer<ByteBuffer> parent) {
		this(parent, true);
	}
	
	public ChunkedWritableByteContainer(WritableContainer<ByteBuffer> parent, boolean writeEnding) {
		this.parent = parent;
		this.writeEnding = writeEnding;
	}
	
	@Override
	public void close() throws IOException {
		parent.close();
	}

	@Override
	public long write(ByteBuffer source) throws IOException {
		long buffered = buffer.remainingData();
		if (buffered > 0 && parent.write(buffer) != buffered) {
			return 0;
		}
		long remainingData = source.remainingData();
		if (remainingData > 0) {
			String hexSize = Integer.toHexString((int) source.remainingData());
			try {
				buffer.write((hexSize + "\r\n").getBytes("ASCII"));
				buffer.write(source);
				buffer.write("\r\n".getBytes("ASCII"));
			}
			catch (UnsupportedEncodingException e) {
				throw new IOException(e);
			}
			parent.write(buffer);
			return remainingData - source.remainingData();
		}
		return 0;
	}

	public void finish(Header...headers) throws IOException {
		try {
			buffer.write("0".getBytes("ASCII"));
			// you can set additional headers at the end of the message
			for (Header header : headers)
				buffer.write(("\r\n" + header.toString()).getBytes("ASCII"));
			// finish with two CRLF
			if (writeEnding)
				buffer.write("\r\n\r\n".getBytes("ASCII"));
			finished = true;
			flush();
		}
		catch (UnsupportedEncodingException e) {
			throw new IOException(e);
		}
		
	}
	
	@Override
	public void flush() throws IOException {
		if (!finished)
			finish();
		if (buffer.remainingData() != parent.write(buffer))
			throw new IOException("There is not enough room in the parent to flush the remaining data");
		parent.flush();
	}

	public boolean isWriteEnding() {
		return writeEnding;
	}

	public void setWriteEnding(boolean writeEnding) {
		this.writeEnding = writeEnding;
	}
}
