package koready_backend.terms.application.exception;

public class RequiredTermsNotAgreedException extends RuntimeException {

	public RequiredTermsNotAgreedException() {
		super("All currently required terms must be agreed.");
	}
}
