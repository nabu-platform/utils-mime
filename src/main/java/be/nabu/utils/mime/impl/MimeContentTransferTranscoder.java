package be.nabu.utils.mime.impl;

import be.nabu.utils.codec.TranscoderUtils;
import be.nabu.utils.codec.api.Transcoder;
import be.nabu.utils.codec.impl.DeflateTranscoder;
import be.nabu.utils.codec.impl.GZIPDecoder;
import be.nabu.utils.codec.impl.GZIPEncoder;
import be.nabu.utils.codec.impl.InflateTranscoder;
import be.nabu.utils.codec.impl.DeflateTranscoder.DeflaterLevel;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.api.WritableContainer;
import be.nabu.utils.mime.api.ContentTransferTranscoder;
import be.nabu.utils.mime.util.ChunkedEncodingReadableByteContainer;
import be.nabu.utils.mime.util.ChunkedReadableByteContainer;
import be.nabu.utils.mime.util.ChunkedWritableByteContainer;

public class MimeContentTransferTranscoder implements ContentTransferTranscoder {

	public boolean optimizeCompression;
	
	public MimeContentTransferTranscoder() {
		// auto construct
	}
	
	public MimeContentTransferTranscoder(boolean optimizeCompression) {
		this.optimizeCompression = optimizeCompression;
	}
	
	@Override
	public ReadableContainer<ByteBuffer> encodeTransfer(String contentTransferEncoding, ReadableContainer<ByteBuffer> decodedContent) {
		Transcoder<ByteBuffer> transcoder = MimeUtils.getEncoder(contentTransferEncoding);
		return transcoder != null ? TranscoderUtils.wrapReadable(decodedContent, transcoder) : decodedContent;
	}

	@Override
	public ReadableContainer<ByteBuffer> decodeTransfer(String contentTransferEncoding, ReadableContainer<ByteBuffer> encodedContent) {
		Transcoder<ByteBuffer> transcoder = MimeUtils.getDecoder(contentTransferEncoding);
		return transcoder != null ? TranscoderUtils.wrapReadable(encodedContent, transcoder) : encodedContent;
	}

	@Override
	public WritableContainer<ByteBuffer> encodeTransfer(String contentTransferEncoding, WritableContainer<ByteBuffer> decodedContent) {
		Transcoder<ByteBuffer> transcoder = MimeUtils.getEncoder(contentTransferEncoding);
		return transcoder != null ? TranscoderUtils.wrapWritable(decodedContent, transcoder) : decodedContent;
	}

	@Override
	public WritableContainer<ByteBuffer> decodeTransfer(String contentTransferEncoding, WritableContainer<ByteBuffer> encodedContent) {
		Transcoder<ByteBuffer> transcoder = MimeUtils.getDecoder(contentTransferEncoding);
		return transcoder != null ? TranscoderUtils.wrapWritable(encodedContent, transcoder) : encodedContent;
	}

	
	@Override
	public ReadableContainer<ByteBuffer> encodeContent(String contentEncoding, ReadableContainer<ByteBuffer> decodedContent) {
		if (contentEncoding != null && contentEncoding.equalsIgnoreCase("chunked")) {
			return new ChunkedEncodingReadableByteContainer(decodedContent, 1024 * 50);
		}
		Transcoder<ByteBuffer> transcoder = null;
		if (contentEncoding != null && contentEncoding.equalsIgnoreCase("gzip"))
			transcoder = new GZIPEncoder(optimizeCompression ? DeflaterLevel.BEST_COMPRESSION : DeflaterLevel.BEST_SPEED);
		else if (contentEncoding != null && contentEncoding.equalsIgnoreCase("deflate"))
			transcoder = new DeflateTranscoder();
		return transcoder != null ? TranscoderUtils.wrapReadable(decodedContent, transcoder) : decodedContent;
	}

	@Override
	public ReadableContainer<ByteBuffer> decodeContent(String contentEncoding, ReadableContainer<ByteBuffer> encodedContent) {
		if (contentEncoding != null && contentEncoding.equalsIgnoreCase("chunked"))
			return new ChunkedReadableByteContainer(encodedContent);
		Transcoder<ByteBuffer> transcoder = null;
		if (contentEncoding != null && contentEncoding.equalsIgnoreCase("gzip"))
			transcoder = new GZIPDecoder();
		else if (contentEncoding != null && contentEncoding.equalsIgnoreCase("deflate"))
			transcoder = new InflateTranscoder();
		return transcoder != null ? TranscoderUtils.wrapReadable(encodedContent, transcoder) : encodedContent;
	}

	@Override
	public WritableContainer<ByteBuffer> encodeContent(String contentEncoding, WritableContainer<ByteBuffer> decodedContent) {
		if (contentEncoding != null && contentEncoding.equalsIgnoreCase("chunked"))
			return new ChunkedWritableByteContainer(decodedContent, true);
		Transcoder<ByteBuffer> transcoder = null;
		if (contentEncoding != null && contentEncoding.equalsIgnoreCase("gzip"))
			transcoder = new GZIPEncoder(optimizeCompression ? DeflaterLevel.BEST_COMPRESSION : DeflaterLevel.BEST_SPEED);
		else if (contentEncoding != null && contentEncoding.equalsIgnoreCase("deflate"))
			transcoder = new DeflateTranscoder();
		return transcoder != null ? TranscoderUtils.wrapWritable(decodedContent, transcoder) : decodedContent;
	}

	@Override
	public WritableContainer<ByteBuffer> decodeContent(String contentEncoding, WritableContainer<ByteBuffer> encodedContent) {
		Transcoder<ByteBuffer> transcoder = null;
		if (contentEncoding != null && contentEncoding.equalsIgnoreCase("gzip"))
			transcoder = new GZIPDecoder();
		else if (contentEncoding != null && contentEncoding.equalsIgnoreCase("deflate"))
			transcoder = new InflateTranscoder();
		return transcoder != null ? TranscoderUtils.wrapWritable(encodedContent, transcoder) : encodedContent;
	}

}
