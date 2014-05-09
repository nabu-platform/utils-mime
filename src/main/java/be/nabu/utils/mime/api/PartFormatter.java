package be.nabu.utils.mime.api;

import java.io.IOException;

import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.WritableContainer;
import be.nabu.utils.mime.impl.FormatException;

public interface PartFormatter {
	public void format(Part part, WritableContainer<ByteBuffer> output) throws IOException, FormatException;
	public ContentTransferTranscoder getTranscoder();
}
