package koready_backend.horitip.application.exception;

public final class HoriTipConcurrentModificationException extends RuntimeException {

	public HoriTipConcurrentModificationException() {
		super("The Hori Tip changed after it was loaded. Refresh and try again.");
	}
}
