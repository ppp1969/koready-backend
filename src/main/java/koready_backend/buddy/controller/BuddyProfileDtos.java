package koready_backend.buddy.controller;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import koready_backend.buddy.application.BuddyProfileService;
import koready_backend.buddy.application.model.BuddyProfileView;
import koready_backend.buddy.domain.BuddySocialLink;
import koready_backend.buddy.domain.BuddyStyle;
import koready_backend.buddy.domain.KoreanLevel;
import koready_backend.buddy.domain.SocialLinkType;
import koready_backend.place.domain.PlaceLanguage;

final class BuddyProfileDtos {

	private BuddyProfileDtos() {
	}

	static MyBuddyProfileResponse from(BuddyProfileService.MyProfileResult result) {
		return new MyBuddyProfileResponse(
			result.exists(),
			result.profile() == null ? null : from(result.profile()));
	}

	static BuddyProfileResponse from(BuddyProfileView profile) {
		return new BuddyProfileResponse(
			profile.profileId(),
			profile.profileImageUrl(),
			profile.nickname(),
			profile.nationality(),
			profile.availableLanguages(),
			profile.koreanLevel(),
			profile.bio(),
			profile.buddyStyles(),
			profile.socialLinks().stream()
				.map(link -> new SocialLinkResponse(link.type(), link.value(), null))
				.toList(),
			profile.profilePublic(),
			profile.snsPublic(),
			profile.allowsMessages(),
			profile.canMessage(),
			profile.blockedByMe(),
			profile.updatedAt());
	}

	record BuddyProfileRequest(
		@Size(max = 2048) String profileImageUrl,
		@NotBlank @Size(max = 30) String nickname,
		@Size(max = 100) String nationality,
		@NotNull @Size(min = 1, max = 2)
		List<@NotNull PlaceLanguage> availableLanguages,
		@NotNull KoreanLevel koreanLevel,
		@Size(max = 500) String bio,
		@NotNull @Size(max = 6) List<@NotNull BuddyStyle> buddyStyles,
		@Size(max = 20) List<@NotNull @Valid SocialLinkInput> socialLinks,
		@NotNull Boolean profilePublic,
		@NotNull Boolean snsPublic,
		@NotNull Boolean allowsMessages
	) {
		BuddyProfileService.UpsertCommand toCommand() {
			List<BuddySocialLink> links = socialLinks == null
				? List.of()
				: socialLinks.stream().map(SocialLinkInput::toDomain).toList();
			return new BuddyProfileService.UpsertCommand(
				profileImageUrl,
				nickname,
				nationality,
				availableLanguages,
				koreanLevel,
				bio,
				buddyStyles,
				links,
				profilePublic,
				snsPublic,
				allowsMessages);
		}
	}

	record SocialLinkInput(
		@NotNull SocialLinkType type,
		@NotBlank @Size(max = 200) String value
	) {
		BuddySocialLink toDomain() {
			return new BuddySocialLink(type, value);
		}
	}

	record MyBuddyProfileResponse(
		boolean exists,
		BuddyProfileResponse profile
	) {
	}

	record BuddyProfileResponse(
		long profileId,
		String profileImageUrl,
		String nickname,
		String nationality,
		List<PlaceLanguage> availableLanguages,
		KoreanLevel koreanLevel,
		String bio,
		List<BuddyStyle> buddyStyles,
		List<SocialLinkResponse> socialLinks,
		boolean profilePublic,
		boolean snsPublic,
		boolean allowsMessages,
		boolean canMessage,
		boolean blockedByMe,
		Instant updatedAt
	) {
	}

	record SocialLinkResponse(
		SocialLinkType type,
		String displayValue,
		String url
	) {
	}
}
