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

import static be.nabu.utils.io.IOUtils.wrap;

import java.io.IOException;
import java.text.ParseException;

import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.DelimitedCharContainer;
import be.nabu.utils.io.api.LimitedReadableContainer;
import be.nabu.utils.io.api.PushbackContainer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.containers.EOFReadableContainer;
import be.nabu.utils.io.containers.chars.ReadableStraightByteToCharContainer;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.api.HeaderProvider;
import be.nabu.utils.mime.impl.MimeUtils;

public class ChunkedReadableByteContainer implements HeaderProvider {

	private EOFReadableContainer<ByteBuffer> parent;
	
	private byte [] single = new byte[1];
	
	private LimitedReadableContainer<ByteBuffer> chunk;
	
	private int maxChunkSize = 1024 * 1024 * 64;
	
	private boolean chunksFinished = false, parentFinished = false;
	
	private Header [] trailingHeaders = new Header[0];
	
	private String partialChunkHeader;
	
	public ChunkedReadableByteContainer(ReadableContainer<ByteBuffer> parent) {
		this.parent = new EOFReadableContainer<ByteBuffer>(parent);
	}
	
	@Override
	public void close() throws IOException {
		parentFinished = true;
		parent.close();
	}

	@Override
	public long read(ByteBuffer target) throws IOException {
		long totalRead = 0;
		while (!chunksFinished && target.remainingSpace() > 0) {
			if (chunk == null || chunk.remainingData() == 0) {
				// the chunk should be followed by a proper CRLF
				if (chunk != null) {
					long delimiterRead = parent.read(wrap(single, false));
					// no data to read the chunk ending
					if (delimiterRead == 0) {
						break;
					}
					if (single[0] == '\r') {
						delimiterRead = parent.read(wrap(single, false));
					}
					if (delimiterRead == 0) {
						break;
					}
					if (single[0] != '\n') {
						throw new IOException("The chunk was not followed by a linefeed but " + single[0]);
					}
					else {
						chunk = null;
					}
				}
				// we need to read bytes until we come to a linefeed which indicates the end of a chunk size
				// read max 1000 characters
				int maxLength = 1000;
				if (partialChunkHeader != null) {
					maxLength -= partialChunkHeader.length();
				}
				DelimitedCharContainer container = IOUtils.delimit(new ReadableStraightByteToCharContainer(IOUtils.limitReadable(parent, 1000)), "\n");
				String content = IOUtils.toString(container);
				if (!container.isDelimiterFound()) {
					if ((1000 - maxLength) + content.length() >= 1000) {
						throw new IOException("Could not find linefeed that delimits the chunk descriptor in a reasonable amount of bytes (1000), read: " + content);
					}
					else {
						if (partialChunkHeader == null) {
							partialChunkHeader = content;
						}
						else {
							partialChunkHeader += content;
						}
						break;
					}
				}
				else if (partialChunkHeader != null) {
					content = partialChunkHeader + content;
					partialChunkHeader = null;
				}
				// strip trailing "\r" which should be there if it is well-formed
				if (content.endsWith("\r"))
					content = content.substring(0, content.length() - 1);
				// ignore any chunk extensions
				int index = content.indexOf(';');
				if (index >= 0)
					content = content.substring(0, index);
				int chunkSize = Integer.decode("0x" + content);
				if (chunkSize == 0) {
					chunksFinished = true;
					break;
				}
				else if (chunkSize > maxChunkSize) {
					throw new IOException("The chunk " + chunkSize + " is too big, max " + maxChunkSize + " allowed");
				}
//				System.out.println("Starting new chunk with size: " + chunkSize);
				chunk = IOUtils.limitReadable(parent, chunkSize);
			}
			long copied = chunk.read(target);
			// chunk is done
			if (copied < 0) {
				chunk = null;
				// @2023-03-17: can trigger a CPU denial of service by sending a chunk of a certain size in a request that has an explicit content-length header _smaller_ than the chunk (so we can never read the end)
				// we didn't explicitly check the parent state, instead we just kept reading until the end of the chunk was found
				// we can't read any more either because the chunk is "complete" or because the parent simply didn't have any more data
				// if we notice that the parent is empty _BEFORE_ the chunk is done, we need to make sure we stop trying to read, otherwise we end up in an unending loop trying to get more data from that parent!
				if (parent.isEOF()) {
					chunksFinished = true;
					parentFinished = true;
				}
				break;
			}
			else if (copied == 0) {
				break;
			}
			totalRead += copied;
		}
		if (chunksFinished && !parentFinished) {
			// if the last chunk is followed by another CRLF, there are no more headers
			parent.read(wrap(single, false));
			if (single[0] == '\r')
				parent.read(wrap(single, false));
			if (single[0] != '\n') {
				PushbackContainer<ByteBuffer> pushback = IOUtils.pushback(parent);
				// push back the single byte we read (if we read a \r without a \n, push back whatever followed the \r)
				pushback.pushback(wrap(single, true));
				try {
					trailingHeaders = MimeUtils.readHeaders(new ReadableStraightByteToCharContainer(pushback));
				}
				catch (ParseException e) {
					throw new IOException(e);
				}
			}
			// the parent is done as well
			parentFinished = true;
		}
		// make sure the chunked readable container indicates a finished stream if the chunking is done
		return totalRead == 0 && chunksFinished && parentFinished ? -1 : totalRead;
	}
	
	public boolean isFinished() {
		return chunksFinished && parentFinished;
	}
	
	@Override
	public Header [] getAdditionalHeaders() {
		return trailingHeaders;
	}

	public int getMaxChunkSize() {
		return maxChunkSize;
	}

	public void setMaxChunkSize(int maxChunkSize) {
		this.maxChunkSize = maxChunkSize;
	}
}
