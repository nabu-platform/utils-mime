package be.nabu.utils.mime.api;

public interface Header {
	public String getName();
	public String getValue();
	public String [] getComments();
}
