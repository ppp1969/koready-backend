package koready_backend.terms.application.exception;

public class InvalidTermAgreementException extends RuntimeException {

	public InvalidTermAgreementException() {
		super("Only each currently published term version may be submitted once.");
	}
}
