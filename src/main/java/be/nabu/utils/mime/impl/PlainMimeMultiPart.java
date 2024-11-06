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
