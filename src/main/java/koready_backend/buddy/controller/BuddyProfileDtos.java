package koready_backend.buddy.controller;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import koready_backend.buddy.application.BuddyProfileService;
import koready_backend.buddy.application.model.BuddyProfileView;
import koready_backend.buddy.domain.BuddySocialLink;
import koready_backend.buddy.domain.BuddyStyle;
import koready_backend.buddy.domain.KoreanLevel;
import koready_backend.buddy.domain.ProfileLanguage;
import koready_backend.buddy.domain.SocialLinkType;
import koready_backend.place.domain.TravelStyle;

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
			profile.travelStyles(),
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
		@NotBlank @Pattern(regexp = "(?i)[A-Z]{2}") String nationalityCode,
		@NotNull @Size(min = 1, max = 5)
		List<@NotNull ProfileLanguage> availableLanguages,
		@NotNull KoreanLevel koreanLevel,
		@NotNull @Size(min = 1, max = 4)
		List<@NotNull TravelStyle> travelStyles,
		@Size(max = 120) String bio,
		@Size(max = 6) List<@NotNull BuddyStyle> buddyStyles,
		@Size(max = 2) List<@NotNull @Valid SocialLinkInput> socialLinks,
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
				nationalityCode,
				availableLanguages,
				koreanLevel,
				travelStyles,
				bio,
				buddyStyles == null ? List.of() : buddyStyles,
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
		String nationalityCode,
		List<ProfileLanguage> availableLanguages,
		KoreanLevel koreanLevel,
		List<TravelStyle> travelStyles,
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
