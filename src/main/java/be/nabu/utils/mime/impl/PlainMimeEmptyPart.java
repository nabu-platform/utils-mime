package be.nabu.utils.mime.impl;

import java.io.IOException;

import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.mime.api.ContentPart;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.api.MultiPart;

public class PlainMimeEmptyPart extends PlainMimePart implements ContentPart {

	public PlainMimeEmptyPart(MultiPart parent, Header...headers) {
		super(parent, headers);
	}

	@Override
	public ReadableContainer<ByteBuffer> getReadable() throws IOException {
		return null;
	}

	@Override
	public boolean isReopenable() {
		return true;
	}
	
}
