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

import java.io.IOException;

import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.mime.api.ContentPart;
import be.nabu.utils.mime.api.MultiPart;

public class ModifiableWrappedContentPart extends PlainMimePart implements ContentPart {

	private ContentPart original;
	
	public ModifiableWrappedContentPart(ContentPart original) {
		super((MultiPart) original.getParent(), original.getHeaders());
		this.original = original;
	}
	
	@Override
	public String getContentType() {
		return original.getContentType();
	}

	@Override
	public String getName() {
		return original.getName();
	}

	@Override
	public ReadableContainer<ByteBuffer> getReadable() throws IOException {
		return original.getReadable();
	}
}
