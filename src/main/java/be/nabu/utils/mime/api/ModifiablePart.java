package be.nabu.utils.mime.api;

public interface ModifiablePart extends Part {
	public void setHeader(Header...headers);
	public void removeHeader(String...names);
}
