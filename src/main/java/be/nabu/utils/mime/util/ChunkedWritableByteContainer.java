package be.nabu.utils.mime.util;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import be.nabu.utils.io.IOUtils;
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
		if (source.remainingData() > 0) {
			String hexSize = Integer.toHexString((int) source.remainingData());
			try {
				buffer.write((hexSize + "\r\n").getBytes("ASCII"));
				buffer.write(source);
				buffer.write("\r\n".getBytes("ASCII"));
			}
			catch (UnsupportedEncodingException e) {
				throw new IOException(e);
			}
		}
		return IOUtils.copyBytes(buffer, parent);
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
		if (buffer.remainingData() != IOUtils.copyBytes(buffer, parent))
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
