package be.nabu.utils.mime.impl;

import java.util.Iterator;

import be.nabu.utils.mime.api.MultiPart;
import be.nabu.utils.mime.api.Part;

public class ModifiableWrappedMultiPart extends PlainMimePart implements MultiPart {

	private MultiPart original;
	
	public ModifiableWrappedMultiPart(MultiPart original) {
		super((MultiPart) original.getParent(), original.getHeaders());
		this.original = original;
	}

	@Override
	public Part getChild(String arg0) {
		return original.getChild(arg0);
	}

	@Override
	public Iterator<Part> iterator() {
		return original.iterator();
	}

}
