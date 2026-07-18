package koready_backend.horitip.application.exception;

public final class HoriTipCodeDuplicatedException extends RuntimeException {

	public HoriTipCodeDuplicatedException(String code) {
		super("Hori Tip code %s is already reserved".formatted(code));
	}
}
