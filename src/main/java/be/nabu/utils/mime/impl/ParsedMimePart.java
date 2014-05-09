package be.nabu.utils.mime.impl;

import java.io.IOException;

import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;

abstract public class ParsedMimePart extends MimePartBase<ParsedMimeMultiPart> {

	private MimeParser parser;
	
	/**
	 * If the part content is only a subset of the contentURI (e.g. in case of a full mime message), this is the (relative) offset in it
	 */
	private long offset;
	
	/**
	 * The offset of the body of the content from the headers (relative)
	 */
	private long bodyOffset;

	/**
	 * If the part content is only a subset of the contentURI (e.g. in case of a full mime message), this is the size of it
	 * If it is 0, it goes to the end of the content
	 */
	private long size;
	
	void setOffset(long offset) {
		this.offset = offset;
	}
	void setSize(long size) {
		this.size = size;
	}
	long getRelativeOffset() {
		return offset;
	}
	long getSize() {
		return size;
	}
	long getAbsoluteOffset() {
		return getParent() == null ? offset : getParent().getAbsoluteOffset() + offset;
	}
	
	ReadableContainer<ByteBuffer> getRawContent() throws IOException {
		ReadableContainer<ByteBuffer> message = null;
		if (getParent() == null) {
			// get the data
			message = getResource().getReadable();
			// skip to the correct position
			message.read(IOUtils.newByteSink(getAbsoluteOffset()));
		}
		else {
			message = getParent().getRawContent();
			message.read(IOUtils.newByteSink(getRelativeOffset()));
		}
		// return a limited view if necessary
		return IOUtils.bufferReadable(getSize() > 0 ? IOUtils.limitReadable(message, getSize()) : message, IOUtils.newByteBuffer(1024*10, true));
	}
	
	ReadableContainer<ByteBuffer> getContent() throws IOException {
		ReadableContainer<ByteBuffer> bytes = getRawContent();
		bytes.read(IOUtils.newByteSink(getBodyOffset()));
		bytes = getParser().getTranscoder().decodeContent(MimeUtils.getTransferEncoding(getHeaders()), bytes);
		// decode any transfer encoding (e.g. base64)
		bytes = getParser().getTranscoder().decodeTransfer(MimeUtils.getContentTransferEncoding(getHeaders()), bytes);
		// decode any content encoding (e.g. gzip)
		bytes = getParser().getTranscoder().decodeContent(MimeUtils.getContentEncoding(getHeaders()), bytes);
		return bytes;
	}
	
	MimeParser getParser() {
		return parser;
	}
	
	void setParser(MimeParser parser) {
		this.parser = parser;
	}
	
	@Override
	protected ReadableResource getResource() {
		ReadableResource resource = super.getResource();
		if (resource == null && getParent() != null)
			resource = getParent().getResource();
		return resource;
	}
	
	long getBodyOffset() {
		return bodyOffset;
	}
	void setBodyOffset(long bodyOffset) {
		this.bodyOffset = bodyOffset;
	}
	
}
