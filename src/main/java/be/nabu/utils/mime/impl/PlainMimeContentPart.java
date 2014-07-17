package be.nabu.utils.mime.impl;

import java.io.Closeable;
import java.io.IOException;

import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.MarkableContainer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.api.ResettableContainer;
import be.nabu.utils.mime.api.ContentPart;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.api.MultiPart;

/**
 * Currently the content part will do a best effort to mark & reset the container
 * This allows you to read the content multiple times (e.g. once yourself to use it and once by the mime formatter)
 * TODO: it may however be interesting to leave this to whoever is controlling the content because now I might call a reset() on a container that can not handle it
 */
public class PlainMimeContentPart extends PlainMimePart implements ContentPart, Closeable {

	private ReadableContainer<ByteBuffer> content;
	
	public PlainMimeContentPart(MultiPart parent, ReadableContainer<ByteBuffer> content, Header...headers) {
		super(parent, headers);
		if (content instanceof MarkableContainer) {
			((MarkableContainer<ByteBuffer>) content).mark();
		}
		this.content = content;
	}

	@Override
	public void close() throws IOException {
		content.close();
	}

	@Override
	public ReadableContainer<ByteBuffer> getReadable() throws IOException {
		if (content instanceof ResettableContainer) {
			((ResettableContainer<ByteBuffer>) content).reset();
		}
		return content;
	}

}
