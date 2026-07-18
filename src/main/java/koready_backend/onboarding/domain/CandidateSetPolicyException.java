package koready_backend.onboarding.domain;

import java.util.List;

public final class CandidateSetPolicyException extends RuntimeException {

	private final Reason reason;
	private final List<Long> placeIds;

	public CandidateSetPolicyException(Reason reason, String message) {
		this(reason, message, List.of());
	}

	public CandidateSetPolicyException(Reason reason, String message, List<Long> placeIds) {
		super(message);
		this.reason = reason;
		this.placeIds = List.copyOf(placeIds);
	}

	public Reason reason() {
		return reason;
	}

	public List<Long> placeIds() {
		return placeIds;
	}

	public enum Reason {
		ITEM_DUPLICATED,
		TOO_MANY_ITEMS,
		REQUIRES_TEN_ITEMS,
		PLACE_NOT_READY,
		NOT_EDITABLE
	}
}
