package be.nabu.utils.mime.api;

import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;

public interface HeaderProvider extends ReadableContainer<ByteBuffer> {
	public Header [] getAdditionalHeaders();
}
