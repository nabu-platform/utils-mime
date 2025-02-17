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

import static be.nabu.utils.io.IOUtils.wrap;

import java.io.IOException;
import java.util.Iterator;
import java.util.Stack;

import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.EventfulReadableContainer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.api.WritableContainer;
import be.nabu.utils.io.buffers.bytes.ByteBufferFactory;
import be.nabu.utils.io.buffers.bytes.DynamicByteBuffer;
import be.nabu.utils.io.buffers.bytes.LimitedByteBuffer;
import be.nabu.utils.mime.api.ContentPart;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.api.MultiPart;
import be.nabu.utils.mime.api.Part;
import be.nabu.utils.mime.util.ChunkedEncodingReadableByteContainer;

public class PullableMimeFormatter extends MimeFormatter implements ReadableContainer<ByteBuffer> {

	private ReadableContainer<ByteBuffer> currentReadable;
	protected volatile EventfulReadableContainer<ByteBuffer> currentEventful;
	
	/**
	 * For multiparts
	 */
	private Stack<Iterator<Part>> partIterators = new Stack<Iterator<Part>>();
	/**
	 * Also for multiparts
	 */
	private Stack<String> boundaries = new Stack<String>();
	
	private DynamicByteBuffer buffer = new DynamicByteBuffer();
	private boolean isClosed = true;
	private boolean footerWritten = false;
	
	public void format(Part part) throws IOException, FormatException {
		// doing a new format, reset
		reset();
		push(part, true);
	}
	
	@Override
	public void format(Part part, WritableContainer<ByteBuffer> output) throws IOException, FormatException {
		format(part);
		try {
			IOUtils.copy(this, output, ByteBufferFactory.getInstance().newInstance(500, true));
		}
		finally {
			close();
		}
	}

	private void reset() {
		isClosed = false;
		buffer.truncate();
		footerWritten = false;
	}
	
	private void push(Part part, boolean root) throws IOException, FormatException {
		// formatted parts can not be streamed at this point
		if (part instanceof FormattablePart) {
			((FormattablePart) part).setFormatter(new MimeFormatter());
			((FormattablePart) part).format(buffer);
			// if it is not part of a multipart, it will handle the footer
			if (partIterators.isEmpty()) {
				footerWritten = true;
			}
		}
		else if (isMultiPart(part)) {
			pushMultiPart((MultiPart) part, root);
		}
		else if (part instanceof ContentPart) {
			pushContentPart((ContentPart) part);
		}
		else
			throw new FormatException("Could not format part of type " + part.getClass().getName() + ", it is not a content part and not a multipart");
	}

	private void pushContentPart(ContentPart part) throws IOException, FormatException {
		formatContentPartHeaders((ContentPart) part, buffer);
		ReadableContainer<ByteBuffer> readable = part.getReadable();
		if (readable != null) {
			if (readable instanceof EventfulReadableContainer) {
				currentEventful = (EventfulReadableContainer<ByteBuffer>) readable;
			}
			else {
				currentEventful = null;
			}
			readable = limitByContentRange(part, readable);
			currentReadable = encodeInput(part, readable);
		}
		else {
			currentReadable = null;
		}
	}
	
	private void pushMultiPart(MultiPart part, boolean root) throws FormatException, IOException {
		formatMultiPartHeaders((MultiPart) part, buffer);
		Header contentType = MimeUtils.getHeader("Content-Type", part.getHeaders());
		if (contentType == null)
			throw new FormatException("No content-type found for multipart");
		String boundary = MimeUtils.getBoundary(contentType);
		if (boundary == null)
			throw new FormatException("No boundary found for multipart");
		
		Iterator<Part> iterator = part.iterator();
		// if there are no parts at all, just write the end boundary and be done with it
		if (!iterator.hasNext()) {
			writeBoundary(buffer, boundary, true);
		}
		else {
			boundaries.push(boundary);
			partIterators.push(iterator);
		}
	}

	@Override
	public void close() throws IOException {
		if (currentReadable != null) {
			currentReadable.close();
		}
		isClosed = true;
	}

	@Override
	public long read(ByteBuffer target) throws IOException {
		long totalRead = 0;
		long remainingSpace = 0;
		while (!isClosed && (remainingSpace = target.remainingSpace()) > 0) {
			// the buffer takes priority above all else, it contains headers etc that were preformatted
			if (buffer.remainingData() > 0) {
				totalRead += target.write(buffer);
			}
			// if no data remains in the buffer, we need to check if we were copying a readable
			else {
				// if there is a readable, use it
				if (currentReadable != null) {
					// @2023-11-30: push everything to the buffer first so we can wrap it in chunked encoding if necessary (only relevant for multiparts!)
					ByteBuffer limitedBuffer = new LimitedByteBuffer(buffer, null, remainingSpace);
					long read = currentReadable.read(limitedBuffer);
					// no more data in readable, close it
					if (read == -1) {
						currentReadable.close();
						currentReadable = null;
						// if part of a larger whole, write ending
						if (!partIterators.isEmpty()) {
							// write the end of whatever part we just wrote
							buffer.write(wrap("\r\n\r\n".getBytes("ASCII"), true));
						}
						// no more parts left, we are done
						else if (isIncludeMainContentTrailingLineFeeds() && !footerWritten) {
							footerWritten = true;
							buffer.write(wrap("\r\n\r\n".getBytes("ASCII"), true));
						}
					}
					// nothing to read?
					else if (read == 0) {
						break;
					}
//					else {
//						totalRead += read;
//					}
				}
				// there is no more readable and still space left, check what part we were iterating on
				else if (!partIterators.isEmpty()) {
					Iterator<Part> iterator = partIterators.peek();
					// if what we just wrote was part of a multipart, write the boundary
					boolean isLast = !iterator.hasNext();
					if (!boundaries.isEmpty()) {
						writeBoundary(buffer, isLast ? boundaries.pop() : boundaries.peek(), isLast);
					}
					// if no more parts in the iterator, remove it
					if (isLast) {
						partIterators.pop();
					}
					else {
						try {
							push(iterator.next(), false);
						}
						catch (FormatException e) {
							throw new IOException("Could not write part", e);
						}
					}
				}
				else {
					isClosed = true;
				}
			}
		}
		return totalRead == 0 && isClosed ? -1 : totalRead;
	}

	protected ReadableContainer<ByteBuffer> encodeInput(Part part, ReadableContainer<ByteBuffer> input) {
		// this assumes the formateContentPartHeaders has been called which will have checked or set the encoding (or thrown an exception)
		String contentTransferEncoding = MimeUtils.getContentTransferEncoding(part.getHeaders());
		String transferEncoding = MimeUtils.getTransferEncoding(part.getHeaders());
		String contentEncoding = MimeUtils.getContentEncoding(part.getHeaders());

		if (!allowBinary && contentTransferEncoding == null && !(part instanceof MultiPart)) {
			contentTransferEncoding = getContentTransferEncoding(part);
		}
		
		// this has to be done in the reverse order from the encodeOutput()
		input = getTranscoder().encodeContent(contentEncoding, input);
		input = getTranscoder().encodeTransfer(contentTransferEncoding, input);
		// the values for contentTransferEncoding are different as they are aimed at mime
		input = getTranscoder().encodeContent(transferEncoding, input);
		// if this formatter does not provide the ending, have the chunked itself provide the ending
		if (input instanceof ChunkedEncodingReadableByteContainer) {
			((ChunkedEncodingReadableByteContainer) input).setWriteEnding(!isIncludeMainContentTrailingLineFeeds());
		}
		return input;
	}

	public boolean isDone() {
		return isClosed;
	}
}
