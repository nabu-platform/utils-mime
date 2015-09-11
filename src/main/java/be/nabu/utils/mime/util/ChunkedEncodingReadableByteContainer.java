package be.nabu.utils.mime.util;

import java.io.IOException;
import java.nio.charset.Charset;

import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.buffers.bytes.CyclicByteBuffer;

/** 
 * Possible optimization (currently not performed because it requires quite a bit of code):
 * IF the buffer is originally empty (no data that we can corrupt in it)
 * AND the source has enough data (currently) left to fit the chunk size (99% of the cases)
 * THEN we get a huge boost by simply assuming the chunk size will be met and write the assumed chunk header first
 * IF we are in the 1% that there is not enough data, we have to wipe the buffer again (hence no initial data allowed) and rewrite it with the actual amount
 * this last bit has a lot of overhead but we are assuming 99/1 ratio's prioritizing big data
 * this also fails if the buffer size is choosen too small (more specifically smaller than the chunk size + the header)
 * 		At this point you need different code (with internal buffers) to handle the larger chunk size)
 */
public class ChunkedEncodingReadableByteContainer implements ReadableContainer<ByteBuffer> {

	private ReadableContainer<ByteBuffer> parent;
	private int chunkSize;
	private boolean isClosed = false;
	private ByteBuffer buffer;
	private byte [] hexSize;
	private byte [] lineFeed = "\r\n".getBytes(Charset.forName("ASCII"));
	private boolean parentDone = false;
	
	public ChunkedEncodingReadableByteContainer(ReadableContainer<ByteBuffer> parent, int chunkSize) {
		this.parent = parent;
		this.chunkSize = chunkSize;
		this.buffer = new CyclicByteBuffer(chunkSize);
		this.hexSize = Integer.toHexString(chunkSize).getBytes(Charset.forName("ASCII"));
	}
	
	@Override
	public void close() throws IOException {
		isClosed = true;
		parent.close();
	}

	@Override
	public long read(ByteBuffer target) throws IOException {
		if (isClosed) {
			return -1;
		}
		long totalRead = 0;
		// if the chunk is not complete, read from parent
		if (buffer.remainingSpace() > 0) {
			long read = parent.read(buffer);
			if (read == -1) {
				parentDone = true;
			}
		}
		// if the parent is done, update the chunk size to match whatever data is left
		if (parentDone) {
			hexSize = Integer.toHexString((int) buffer.remainingData()).getBytes(Charset.forName("ASCII"));
		}
		// the target buffer must at least accommodate the parts that are not saved
		if ((parentDone || buffer.remainingSpace() == 0) && (target.remainingSpace() > hexSize.length + lineFeed.length)) {
			totalRead += target.write(hexSize);
			totalRead += target.write(lineFeed);
			totalRead += target.write(buffer);
		}
		if (parentDone && buffer.remainingData() == 0) {
			isClosed = true;
		}
		return totalRead == 0 && isClosed ? -1 : totalRead;
	}

	public int getChunkSize() {
		return chunkSize;
	}
}
