package be.nabu.utils.mime.impl;

import java.io.IOException;

import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.mime.api.ContentPart;

public class ParsedMimeBinaryPart extends ParsedMimePart implements ContentPart {

	@Override
	final public ReadableContainer<ByteBuffer> getReadable() throws IOException {
		return getContent();
	}

}
