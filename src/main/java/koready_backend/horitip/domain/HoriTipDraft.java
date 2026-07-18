package koready_backend.horitip.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import koready_backend.place.domain.PlaceLanguage;

public record HoriTipDraft(
	HoriTipPlacement placement,
	int priority,
	HoriTipScope scope,
	HoriTipTrigger trigger,
	List<HoriTipTranslation> translations,
	Instant validFrom,
	Instant validUntil,
	String operatorNote
) {

	public HoriTipDraft {
		if (placement == null || scope == null || trigger == null || translations == null) {
			throw invalid("Hori Tip editable fields are required");
		}
		if (priority < 0 || priority > 1000) {
			throw invalid("Hori Tip priority must be between 0 and 1000");
		}
		translations = List.copyOf(translations);
		if (translations.isEmpty() || translations.size() > 2
			|| new HashSet<>(translations.stream()
				.map(HoriTipTranslation::language)
				.toList()).size() != translations.size()) {
			throw invalid("Hori Tip translations require one or two unique languages");
		}
		if (placement == HoriTipPlacement.AFTER_SEGMENT && !trigger.hasSegmentCondition()) {
			throw invalid("AFTER_SEGMENT requires at least one segment matching condition");
		}
		if (validFrom != null && validUntil != null && !validFrom.isBefore(validUntil)) {
			throw invalid("Hori Tip validFrom must be earlier than validUntil");
		}
		if (operatorNote != null) {
			operatorNote = operatorNote.strip();
			if (operatorNote.length() > 500) {
				throw invalid("A Hori Tip operator note can contain at most 500 characters");
			}
			if (operatorNote.isEmpty()) {
				operatorNote = null;
			}
		}
	}

	public void requireActivatable() {
		Set<PlaceLanguage> languages = new HashSet<>(translations.stream()
			.map(HoriTipTranslation::language)
			.toList());
		if (!languages.equals(Set.of(PlaceLanguage.KO, PlaceLanguage.EN))) {
			throw new HoriTipPolicyException(
				HoriTipPolicyException.Reason.ACTIVATION_INVALID,
				"An active Hori Tip requires both Korean and English bodies");
		}
		if (scope.scopeType() == HoriTipScopeType.ALL_ROUTES && !trigger.hasAnyCondition()) {
			throw new HoriTipPolicyException(
				HoriTipPolicyException.Reason.ACTIVATION_INVALID,
				"An ALL_ROUTES Hori Tip requires at least one matching condition");
		}
	}

	private static HoriTipPolicyException invalid(String message) {
		return new HoriTipPolicyException(HoriTipPolicyException.Reason.RULE_INVALID, message);
	}
}
