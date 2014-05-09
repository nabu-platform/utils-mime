package be.nabu.utils.mime.api;

import java.io.IOException;
import java.text.ParseException;

import be.nabu.libs.resources.api.ReadableResource;

public interface PartParser {
	public Part parse(ReadableResource resource) throws ParseException, IOException;
}
