package be.nabu.utils.mime.impl;

import java.io.IOException;

import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.WritableContainer;

public class NonPropagatingClose implements WritableContainer<ByteBuffer> {

	private WritableContainer<ByteBuffer> parent;
	
	public NonPropagatingClose(WritableContainer<ByteBuffer> parent) {
		this.parent = parent;
	}
	
	@Override
	public void close() throws IOException {
		// do NOT propagate
		System.out.println(">>>>>>>>>> not propagating close!");
	}

	@Override
	public long write(ByteBuffer source) throws IOException {
		return parent.write(source);
	}

	@Override
	public void flush() {
		System.out.println(">>>>>>>>>> Not propagating flush");
	}

}
