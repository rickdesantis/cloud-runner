package it.cloud.utils;

public class CloudException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6021816809205475200L;

	public CloudException() {
		super();
	}

	public CloudException(String message) {
		super(message);
	}

	public CloudException(Throwable cause) {
		super(cause);
	}

	public CloudException(String message, Throwable cause) {
		super(message, cause);
	}

	public CloudException(String message, Throwable cause,
			boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
