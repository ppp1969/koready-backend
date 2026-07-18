package koready_backend.horitip.application.exception;

public final class HoriTipNotFoundException extends RuntimeException {

	public HoriTipNotFoundException(long id) {
		super("Hori Tip %d was not found".formatted(id));
	}
}
