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
