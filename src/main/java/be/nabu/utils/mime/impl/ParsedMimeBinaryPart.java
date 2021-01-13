package be.nabu.utils.mime.impl;

import java.io.IOException;

import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.mime.api.ModifiableContentPart;

public class ParsedMimeBinaryPart extends ParsedMimePart implements ModifiableContentPart {

	private boolean reopenable;
	
	@Override
	final public ReadableContainer<ByteBuffer> getReadable() throws IOException {
		return getContent();
	}

	@Override
	public void setReopenable(boolean reopenable) {
		this.reopenable = reopenable;
	}

	@Override
	public boolean isReopenable() {
		return reopenable;
	}

}
