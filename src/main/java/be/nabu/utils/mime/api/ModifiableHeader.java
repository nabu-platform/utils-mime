package be.nabu.utils.mime.api;

public interface ModifiableHeader extends Header {
	public void setName(String name);
	public void setValue(String value);
	public void addComment(String...comments);
}
