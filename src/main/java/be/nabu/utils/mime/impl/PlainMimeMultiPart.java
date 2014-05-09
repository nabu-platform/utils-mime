package be.nabu.utils.mime.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.api.MultiPart;
import be.nabu.utils.mime.api.Part;

public class PlainMimeMultiPart extends PlainMimePart implements MultiPart {

	private List<Part> children = new ArrayList<Part>();
	
	public PlainMimeMultiPart(MultiPart parent, Header...headers) {
		super(parent, headers);
	}
	
	public void addChild(Part...children) {
		this.children.addAll(Arrays.asList(children));
	}

	@Override
	public Part getChild(String name) {
		for (Part child : children) {
			if (child.getName().equals(name))
				return child;
		}
		return null;
	}

	@Override
	public Iterator<Part> iterator() {
		return children.iterator();
	}

}
