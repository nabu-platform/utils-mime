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

package be.nabu.utils.mime.api;

import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.api.WritableContainer;


public interface ContentTransferTranscoder {
	public ReadableContainer<ByteBuffer> encodeTransfer(String contentTransferEncoding, ReadableContainer<ByteBuffer> decodedContent);
	public ReadableContainer<ByteBuffer> decodeTransfer(String contentTransferEncoding, ReadableContainer<ByteBuffer> encodedContent);
	
	public WritableContainer<ByteBuffer> encodeTransfer(String contentTransferEncoding, WritableContainer<ByteBuffer> decodedContent);
	public WritableContainer<ByteBuffer> decodeTransfer(String contentTransferEncoding, WritableContainer<ByteBuffer> encodedContent);
	
	public ReadableContainer<ByteBuffer> encodeContent(String contentEncoding, ReadableContainer<ByteBuffer> decodedContent);
	public ReadableContainer<ByteBuffer> decodeContent(String contentEncoding, ReadableContainer<ByteBuffer> encodedContent);
	
	public WritableContainer<ByteBuffer> encodeContent(String contentEncoding, WritableContainer<ByteBuffer> decodedContent);
	public WritableContainer<ByteBuffer> decodeContent(String contentEncoding, WritableContainer<ByteBuffer> encodedContent);
	
}
