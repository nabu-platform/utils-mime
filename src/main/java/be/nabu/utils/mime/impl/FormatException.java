package be.nabu.utils.mime.impl;

public class FormatException extends Exception {

	private static final long serialVersionUID = 5313569965350428303L;

	public FormatException() {
		super();
	}

	public FormatException(String arg0, Throwable arg1, boolean arg2,
			boolean arg3) {
		super(arg0, arg1, arg2, arg3);
	}

	public FormatException(String arg0, Throwable arg1) {
		super(arg0, arg1);
	}

	public FormatException(String arg0) {
		super(arg0);
	}

	public FormatException(Throwable arg0) {
		super(arg0);
	}

}
