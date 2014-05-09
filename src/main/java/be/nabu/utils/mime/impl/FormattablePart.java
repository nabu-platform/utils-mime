package be.nabu.utils.mime.impl;

import java.io.IOException;

import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.WritableContainer;
import be.nabu.utils.mime.api.PartFormatter;

public interface FormattablePart {
	public void format(WritableContainer<ByteBuffer> output) throws IOException, FormatException;
	public void setFormatter(PartFormatter formatter);
}
