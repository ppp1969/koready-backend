package koready_backend.buddy.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

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

	public BuddyProfileDraft {
		profileImageUrl = imageUrl(profileImageUrl);
		nickname = required(nickname, 30, "Nickname");
		nationality = optional(nationality, 100, "Nationality");
		availableLanguages = copyDistinct(
			availableLanguages, true, "Available languages");
		if (availableLanguages.size() > 5) {
			throw new IllegalArgumentException(
				"Available languages must not contain more than 5 values");
		}
		koreanLevel = Objects.requireNonNull(koreanLevel, "Korean level is required");
		travelStyles = copyDistinct(travelStyles, false, "Travel styles");
		if (travelStyles.size() > 4) {
			throw new IllegalArgumentException(
				"Travel styles must not contain more than 4 values");
		}
		bio = optional(bio, 500, "Bio");
		buddyStyles = copyDistinct(buddyStyles, false, "Buddy styles");
		socialLinks = copy(socialLinks, "Social links");
		if (socialLinks.stream().map(BuddySocialLink::type).distinct().count()
			!= socialLinks.size()) {
			throw new IllegalArgumentException(
				"Social links must not contain duplicate platforms");
		}
	}

	public BuddyProfileDraft(
		String profileImageUrl,
		String nickname,
		String nationality,
		List<ProfileLanguage> availableLanguages,
		KoreanLevel koreanLevel,
		String bio,
		List<BuddyStyle> buddyStyles,
		List<BuddySocialLink> socialLinks,
		boolean profilePublic,
		boolean snsPublic,
		boolean allowsMessages
	) {
		this(
			profileImageUrl,
			nickname,
			nationality,
			availableLanguages,
			koreanLevel,
			List.of(),
			bio,
			buddyStyles,
			socialLinks,
			profilePublic,
			snsPublic,
			allowsMessages);
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
		try {
			URI uri = new URI(normalized);
			String scheme = uri.getScheme() == null
				? ""
				: uri.getScheme().toLowerCase(Locale.ROOT);
			if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
				throw new IllegalArgumentException(
					"Profile image URL must be an absolute HTTP(S) URL");
			}
			return normalized;
		} catch (URISyntaxException exception) {
			throw new IllegalArgumentException(
				"Profile image URL must be an absolute HTTP(S) URL", exception);
		}
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
