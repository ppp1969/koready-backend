package koready_backend.horitip.domain;

import koready_backend.place.domain.PlaceLanguage;

public record HoriTipTranslation(PlaceLanguage language, String body) {

	public HoriTipTranslation {
		if (language == null || body == null || body.isBlank()) {
			throw invalid("A Hori Tip translation requires a language and body");
		}
		body = body.strip();
		if (body.length() > 300) {
			throw invalid("A Hori Tip translation body can contain at most 300 characters");
		}
	}

	private static HoriTipPolicyException invalid(String message) {
		return new HoriTipPolicyException(HoriTipPolicyException.Reason.RULE_INVALID, message);
	}
}
