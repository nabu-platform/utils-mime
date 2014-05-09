package be.nabu.utils.mime.api;

import be.nabu.libs.resources.api.Resource;

public interface Part extends Resource {
	public Header [] getHeaders();
}
