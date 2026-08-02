package koready_backend.terms.application.exception;

public class TermsUserUnavailableException extends RuntimeException {

	public TermsUserUnavailableException() {
		super("The authenticated user is unavailable.");
	}
}
