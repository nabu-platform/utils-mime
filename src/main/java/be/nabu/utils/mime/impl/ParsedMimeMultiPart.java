package be.nabu.utils.mime.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import be.nabu.libs.resources.api.Resource;
import be.nabu.utils.mime.api.MultiPart;
import be.nabu.utils.mime.api.Part;

public class ParsedMimeMultiPart extends ParsedMimePart implements MultiPart {

	private List<Part> parts = new ArrayList<Part>();
	
	void addParts(ParsedMimePart...parts) {
		this.parts.addAll(Arrays.asList(parts));
	}

	@Override
	public Iterator<Part> iterator() {
		return parts.iterator();
	}

	@Override
	public Part getChild(String name) {
		for (Part part : parts) {
			if (part.getName().equals(name))
				return part;
		}
		return null;
	}

	@Override
	public String getContentType() {
		return Resource.CONTENT_TYPE_DIRECTORY;
	}

}
