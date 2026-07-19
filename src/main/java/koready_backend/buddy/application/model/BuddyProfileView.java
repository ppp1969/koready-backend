package koready_backend.buddy.application.model;

import java.time.Instant;
import java.util.List;

import koready_backend.buddy.domain.BuddySocialLink;
import koready_backend.buddy.domain.BuddyStyle;
import koready_backend.buddy.domain.KoreanLevel;
import koready_backend.place.domain.PlaceLanguage;

public record BuddyProfileView(
	long profileId,
	String profileImageUrl,
	String nickname,
	String nationality,
	List<PlaceLanguage> availableLanguages,
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
}
