package koready_backend.user.domain;

public enum SignupStatus {
	NEED_TERMS,
	NEED_LANGUAGE,
	NEED_ONBOARDING,
	COMPLETED;

	public SignupStatus afterLanguageSelection() {
		return this == NEED_LANGUAGE ? NEED_ONBOARDING : this;
	}

	public NextStep nextStep() {
		return switch (this) {
			case NEED_TERMS -> NextStep.TERMS;
			case NEED_LANGUAGE -> NextStep.LANGUAGE;
			case NEED_ONBOARDING -> NextStep.ONBOARDING;
			case COMPLETED -> NextStep.COMPLETED;
		};
	}
}
