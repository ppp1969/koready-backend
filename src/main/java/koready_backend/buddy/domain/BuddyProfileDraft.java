package koready_backend.buddy.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import koready_backend.place.domain.TravelStyle;

public record BuddyProfileDraft(
	String profileImageUrl,
	String nickname,
	String nationality,
	List<ProfileLanguage> availableLanguages,
	KoreanLevel koreanLevel,
	List<TravelStyle> travelStyles,
	String bio,
	List<BuddyStyle> buddyStyles,
	List<BuddySocialLink> socialLinks,
	boolean profilePublic,
	boolean snsPublic,
	boolean allowsMessages
) {

	private static final Set<String> ISO_COUNTRY_CODES =
		Set.of(Locale.getISOCountries());

	public BuddyProfileDraft {
		profileImageUrl = imageUrl(profileImageUrl);
		nickname = required(nickname, 30, "Nickname");
		nationality = countryCode(nationality);
		availableLanguages = copyDistinct(
			availableLanguages, true, "Available languages");
		if (availableLanguages.size() > 5) {
			throw new IllegalArgumentException(
				"Available languages must not contain more than 5 values");
		}
		koreanLevel = Objects.requireNonNull(koreanLevel, "Korean level is required");
		travelStyles = copyDistinct(travelStyles, true, "Travel styles");
		if (travelStyles.size() > 4) {
			throw new IllegalArgumentException(
				"Travel styles must not contain more than 4 values");
		}
		bio = optional(bio, 120, "Bio");
		buddyStyles = copyDistinct(buddyStyles, false, "Buddy styles");
		socialLinks = copy(socialLinks, "Social links");
		if (socialLinks.size() > 2) {
			throw new IllegalArgumentException(
				"Social links must not contain more than 2 values");
		}
		if (socialLinks.stream().map(BuddySocialLink::type).distinct().count()
			!= socialLinks.size()) {
			throw new IllegalArgumentException(
				"Social links must not contain duplicate platforms");
		}
	}

	private static String countryCode(String value) {
		String normalized = Objects.requireNonNull(
			value, "Nationality is required").trim().toUpperCase(Locale.ROOT);
		if (!ISO_COUNTRY_CODES.contains(normalized)) {
			throw new IllegalArgumentException(
				"Nationality must be an ISO 3166-1 alpha-2 country code");
		}
		return normalized;
	}

	private static String required(String value, int maxLength, String field) {
		String normalized = Objects.requireNonNull(value, field + " is required").trim();
		if (normalized.isEmpty() || normalized.length() > maxLength) {
			throw new IllegalArgumentException(
				field + " must be 1 to " + maxLength + " characters");
		}
		return normalized;
	}

	private static String optional(String value, int maxLength, String field) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		if (normalized.isEmpty()) {
			return null;
		}
		if (normalized.length() > maxLength) {
			throw new IllegalArgumentException(
				field + " must not exceed " + maxLength + " characters");
		}
		return normalized;
	}

	private static String imageUrl(String value) {
		String normalized = optional(value, 2048, "Profile image URL");
		if (normalized == null) {
			return null;
		}
		if (!normalized.matches(
			"^/api/v1/profile-images/img_[0-9a-f]{32}$")) {
			throw new IllegalArgumentException(
				"Profile image URL must come from a completed upload");
		}
		return normalized;
	}

	private static <T> List<T> copyDistinct(
		List<T> values,
		boolean required,
		String field
	) {
		List<T> copied = copy(values, field);
		if (required && copied.isEmpty()) {
			throw new IllegalArgumentException(field + " must not be empty");
		}
		if (new HashSet<>(copied).size() != copied.size()) {
			throw new IllegalArgumentException(field + " must not contain duplicates");
		}
		return copied;
	}

	private static <T> List<T> copy(List<T> values, String field) {
		Objects.requireNonNull(values, field + " are required");
		if (values.stream().anyMatch(Objects::isNull)) {
			throw new IllegalArgumentException(field + " must not contain null values");
		}
		return List.copyOf(values);
	}
}
