package be.nabu.utils.mime.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.api.MultiPart;
import be.nabu.utils.mime.api.Part;

abstract public class MimePartBase<T extends MultiPart> implements Part {

	private List<Header> headers = new ArrayList<Header>();

	/**
	 * The URI pointing to the content of this part
	 */
	private ReadableResource resource;
	
	/**
	 * The parent multipart this belongs to (if any)
	 */
	private T parentPart;
	
	/**
	 * The sequence number of this part in the parent
	 * If there is no "name" of the element, it will instead make up a name based on this if there is a parent
	 */
	private int partNumber;
	
	@Override
	public Header[] getHeaders() {
		return headers.toArray(new Header[headers.size()]);
	}
	
	protected void setHeader(Header...headers){
		this.headers.addAll(Arrays.asList(headers));
	}
	
	@Override
	public String getContentType() {
		return MimeUtils.getContentType(getHeaders());
	}

	@Override
	public String getName() {
		String name = MimeUtils.getName(getHeaders());
		if (name == null && parentPart != null)
			name = "part" + partNumber;
		return name;
	}

	@Override
	public T getParent() {
		return parentPart;
	}
	
	protected void setParent(T parent, int partNumber) {
		this.parentPart = parent;
		this.partNumber = partNumber;
	}

	protected ReadableResource getResource() {
		return resource;
	}
	
	protected void setResource(ReadableResource resource) {
		this.resource = resource;
	}
	
	int getPartNumber() {
		return partNumber;
	}

}
