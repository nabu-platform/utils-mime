package be.nabu.utils.mime.api;

public interface ExpectContinueHandler {
	public boolean shouldContinue(Header...headers);
}
