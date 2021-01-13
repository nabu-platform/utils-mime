package be.nabu.utils.mime.api;

public interface ModifiableContentPart extends ContentPart, ModifiablePart {
	public void setReopenable(boolean reopenable);
}
