package be.nabu.utils.mime.api;

import be.nabu.libs.resources.api.ReadableResource;

public interface ContentPart extends Part, ReadableResource {
	// this indicates whether the content can be read multiple times
	// by default we should assume that we can only read it once
	public default boolean isReopenable() {
		return false;
	}
}
