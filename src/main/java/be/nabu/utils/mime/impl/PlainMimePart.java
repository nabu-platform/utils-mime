package be.nabu.utils.mime.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.api.ModifiablePart;
import be.nabu.utils.mime.api.MultiPart;

abstract public class PlainMimePart implements ModifiablePart {
	
	private MultiPart parent;
	private List<Header> headers = new ArrayList<Header>();

	public PlainMimePart(MultiPart parent, Header...headers) {
		this.parent = parent;
		setHeader(headers);
	}
	
	@Override
	public Header[] getHeaders() {
		return headers.toArray(new Header[headers.size()]);
	}
	
	@Override
	public void setHeader(Header...headers) {
		this.headers.addAll(Arrays.asList(headers));
	}
	
	@Override
	public void removeHeader(String...names) {
		if (names.length > 0) {
			List<String> list = new ArrayList<String>();
			for (String name : names)
				list.add(name.toLowerCase());
			Iterator<Header> iterator = headers.iterator();
			while (iterator.hasNext()) {
				Header next = iterator.next();
				if (list.contains(next.getName().toLowerCase()))
					iterator.remove();
			}
		}
	}
	
	@Override
	public ResourceContainer<?> getParent() {
		return parent;
	}
	
	@Override
	public String getContentType() {
		return MimeUtils.getContentType(getHeaders());
	}

	@Override
	public String getName() {
		return MimeUtils.getName(getHeaders());
	}
}
