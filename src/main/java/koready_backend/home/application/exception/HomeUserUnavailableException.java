package koready_backend.home.application.exception;

public final class HomeUserUnavailableException extends RuntimeException {

	public HomeUserUnavailableException() {
		super("Authenticated user is unavailable");
	}
}
