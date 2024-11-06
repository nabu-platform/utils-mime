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

import java.io.Closeable;
import java.io.IOException;

import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.MarkableContainer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.api.ResettableContainer;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.api.ModifiableContentPart;
import be.nabu.utils.mime.api.MultiPart;

/**
 * Currently the content part will do a best effort to mark & reset the container
 * This allows you to read the content multiple times (e.g. once yourself to use it and once by the mime formatter)
 * TODO: it may however be interesting to leave this to whoever is controlling the content because now I might call a reset() on a container that can not handle it
 */
public class PlainMimeContentPart extends PlainMimePart implements ModifiableContentPart, Closeable {

	private ReadableContainer<ByteBuffer> content;
	private boolean reopenable = false;
	
	public PlainMimeContentPart(MultiPart parent, ReadableContainer<ByteBuffer> content, Header...headers) {
		super(parent, headers);
		if (content instanceof MarkableContainer) {
			((MarkableContainer<ByteBuffer>) content).mark();
		}
		this.content = content;
	}

	@Override
	public void close() throws IOException {
		content.close();
	}

	@Override
	public ReadableContainer<ByteBuffer> getReadable() throws IOException {
		if (content instanceof ResettableContainer) {
			((ResettableContainer<ByteBuffer>) content).reset();
		}
		return content;
	}

	@Override
	public boolean isReopenable() {
		return reopenable;
	}

	@Override
	public void setReopenable(boolean reopenable) {
		this.reopenable = reopenable;
	}
	
}
