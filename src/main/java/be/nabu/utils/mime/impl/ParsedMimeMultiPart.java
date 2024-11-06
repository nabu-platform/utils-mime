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
