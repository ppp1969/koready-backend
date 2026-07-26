package koready_backend.buddy.application.model;

import java.time.Instant;
import java.util.List;

import koready_backend.buddy.domain.BuddySocialLink;
import koready_backend.buddy.domain.BuddyStyle;
import koready_backend.buddy.domain.KoreanLevel;
import koready_backend.buddy.domain.ProfileLanguage;
import koready_backend.place.domain.TravelStyle;

public record BuddyProfileView(
	long profileId,
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
	boolean allowsMessages,
	boolean canMessage,
	boolean blockedByMe,
	Instant updatedAt
) {

	public BuddyProfileView(
		long profileId,
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
		boolean allowsMessages,
		boolean canMessage,
		boolean blockedByMe,
		Instant updatedAt
	) {
		this(
			profileId,
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
			allowsMessages,
			canMessage,
			blockedByMe,
			updatedAt);
	}
}
