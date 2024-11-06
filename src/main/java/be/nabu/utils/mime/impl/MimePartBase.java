/*
* Copyright (C) 2014 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.utils.mime.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.api.ModifiablePart;
import be.nabu.utils.mime.api.MultiPart;

abstract public class MimePartBase<T extends MultiPart> implements ModifiablePart {

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
	
	@Override
	public void setHeader(Header...headers){
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

}
