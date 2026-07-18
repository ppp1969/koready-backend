package koready_backend.horitip.application.exception;

public final class InvalidHoriTipCursorException extends RuntimeException {

	public InvalidHoriTipCursorException() {
		super("The Hori Tip cursor is invalid for the current filters");
	}
}
