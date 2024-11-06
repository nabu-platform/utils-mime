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
import java.nio.charset.Charset;

import be.nabu.utils.io.IOUtils;
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
	private boolean finalizedChunksize = false;
	private ReadableContainer<ByteBuffer> readyChunk;
	private int remainingChunkSize;
	private boolean writeEnding = true;
	
	public ChunkedEncodingReadableByteContainer(ReadableContainer<ByteBuffer> parent, int chunkSize) {
		this.parent = parent;
		this.chunkSize = chunkSize;
		this.hexSize = Integer.toHexString(chunkSize).getBytes(Charset.forName("ASCII"));
		// account for the hex header, the linefeed after the header, the linefeed after the chunk and optionally the linefeed after all the chunking is done
		this.buffer = new CyclicByteBuffer(chunkSize);
	}
	
	@Override
	public void close() throws IOException {
		isClosed = true;
		parent.close();
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public long read(ByteBuffer target) throws IOException {
		if (isClosed) {
			return -1;
		}
		long totalRead = 0;
		// try to prepare a chunk if necessary
		if (readyChunk == null) {
			// if the chunk is not complete, read from parent
			while (buffer.remainingSpace() > 0 && !parentDone) {
				long read = parent.read(buffer);
				if (read == -1) {
					parentDone = true;
				}
				else if (read == 0) {
					break;
				}
			}
			// if the parent is done, update the chunk size to match whatever data is left
			if (parentDone && !finalizedChunksize) {
				finalizedChunksize = true;
				hexSize = Integer.toHexString((int) buffer.remainingData()).getBytes(Charset.forName("ASCII"));
			}
			// if we have to write out a chunk, start writing, for the end of the parent, add an additional linefeed
			if (parentDone || buffer.remainingSpace() == 0) {
				// finish the chunk
				readyChunk = IOUtils.chain(false, IOUtils.wrap(hexSize, true), IOUtils.wrap(lineFeed, true), buffer, IOUtils.wrap(lineFeed, true));
				remainingChunkSize = (int) (buffer.remainingData() + hexSize.length + (lineFeed.length * 2));
				// if the parent is done, write the trailing "0" sized element
				if (parentDone) {
					readyChunk = IOUtils.chain(false, readyChunk, IOUtils.wrap("0".getBytes("ASCII"), true));
					remainingChunkSize += 1;
					if (writeEnding) {
						readyChunk = IOUtils.chain(false, readyChunk, IOUtils.wrap(lineFeed, true), IOUtils.wrap(lineFeed, true));
						remainingChunkSize += lineFeed.length * 2;
					}
				}
			}
		}
		if (readyChunk != null) {
			long read = readyChunk.read(target);
			totalRead += read;
			remainingChunkSize -= read;
			if (remainingChunkSize == 0) {
				readyChunk = null;
			}
		}
		if (parentDone && readyChunk == null) {
			isClosed = true;
		}
		return totalRead == 0 && isClosed ? -1 : totalRead;
	}

	public int getChunkSize() {
		return chunkSize;
	}

	public boolean isWriteEnding() {
		return writeEnding;
	}

	public void setWriteEnding(boolean writeEnding) {
		this.writeEnding = writeEnding;
	}
}
