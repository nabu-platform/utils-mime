package be.nabu.utils.mime.impl;

import java.io.IOException;
import java.text.ParseException;

public interface ParseablePart {
	public void parse() throws ParseException, IOException;
}
