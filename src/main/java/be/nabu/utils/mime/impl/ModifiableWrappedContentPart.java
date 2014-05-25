package be.nabu.utils.mime.impl;

import java.io.IOException;

import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.mime.api.ContentPart;
import be.nabu.utils.mime.api.MultiPart;

public class ModifiableWrappedContentPart extends PlainMimePart implements ContentPart {

	private ContentPart original;
	
	public ModifiableWrappedContentPart(ContentPart original) {
		super((MultiPart) original.getParent(), original.getHeaders());
		this.original = original;
	}
	
	@Override
	public String getContentType() {
		return original.getContentType();
	}

	@Override
	public String getName() {
		return original.getName();
	}

	@Override
	public ReadableContainer<ByteBuffer> getReadable() throws IOException {
		return original.getReadable();
	}
}
