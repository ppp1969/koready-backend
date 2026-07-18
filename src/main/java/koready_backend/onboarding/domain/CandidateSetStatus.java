package koready_backend.onboarding.domain;

public enum CandidateSetStatus {
	DRAFT,
	PUBLISHED,
	ARCHIVED;

	public void requireEditable() {
		if (this != DRAFT) {
			throw new CandidateSetPolicyException(
				CandidateSetPolicyException.Reason.NOT_EDITABLE,
				"Only draft candidate sets can be edited");
		}
	}
}
