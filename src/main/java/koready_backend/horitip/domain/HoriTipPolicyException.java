package koready_backend.horitip.domain;

public final class HoriTipPolicyException extends RuntimeException {

	private final Reason reason;

	public HoriTipPolicyException(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}

	public enum Reason {
		RULE_INVALID,
		ACTIVATION_INVALID,
		NOT_EDITABLE
	}
}
