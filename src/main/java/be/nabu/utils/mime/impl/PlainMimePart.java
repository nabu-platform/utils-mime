package be.nabu.utils.mime.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.api.MultiPart;
import be.nabu.utils.mime.api.Part;

abstract public class PlainMimePart implements Part {
	
	private MultiPart parent;
	private List<Header> headers = new ArrayList<Header>();

	public PlainMimePart(MultiPart parent) {
		this.parent = parent;
	}
	
	@Override
	public Header[] getHeaders() {
		return headers.toArray(new Header[headers.size()]);
	}
	
	public void setHeader(Header...headers) {
		this.headers.addAll(Arrays.asList(headers));
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
