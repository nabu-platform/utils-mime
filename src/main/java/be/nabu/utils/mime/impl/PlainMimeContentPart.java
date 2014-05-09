package be.nabu.utils.mime.impl;

import java.io.Closeable;
import java.io.IOException;

import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.api.ResettableContainer;
import be.nabu.utils.mime.api.ContentPart;
import be.nabu.utils.mime.api.MultiPart;

public class PlainMimeContentPart extends PlainMimePart implements ContentPart, Closeable {

	private ReadableContainer<ByteBuffer> content;
	
	public PlainMimeContentPart(MultiPart parent, ReadableContainer<ByteBuffer> content) {
		super(parent);
		this.content = content;
	}

	@Override
	public void close() throws IOException {
		content.close();
	}

	@Override
	public ReadableContainer<ByteBuffer> getReadable() throws IOException {
		if (content instanceof ResettableContainer)
			((ResettableContainer<ByteBuffer>) content).reset();
		return content;
	}

}
