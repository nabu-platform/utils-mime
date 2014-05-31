package be.nabu.utils.mime.impl;

import be.nabu.utils.mime.api.ExpectContinueHandler;
import be.nabu.utils.mime.api.Header;

public class AlwaysContinue implements ExpectContinueHandler {

	@Override
	public boolean shouldContinue(Header... headers) {
		return true;
	}

}
