package koready_backend.buddy.application;

import java.util.List;

import koready_backend.buddy.application.model.BuddyProfileView;
import koready_backend.buddy.application.port.BuddyProfileRepository.BuddyProfileRecord;
import koready_backend.buddy.domain.BuddyProfileDraft;
import koready_backend.buddy.domain.BuddySocialLink;

final class BuddyProfileViews {

	private BuddyProfileViews() {
	}

	static BuddyProfileView forOwner(BuddyProfileRecord record) {
		return view(record, record.profile().socialLinks(), false, false);
	}

	static BuddyProfileView forPublic(
		BuddyProfileRecord record,
		boolean canMessage,
		boolean blockedByRequester
	) {
		List<BuddySocialLink> socialLinks = record.profile().snsPublic()
			? record.profile().socialLinks()
			: List.of();
		return view(record, socialLinks, canMessage, blockedByRequester);
	}

	private static BuddyProfileView view(
		BuddyProfileRecord record,
		List<BuddySocialLink> socialLinks,
		boolean canMessage,
		boolean blockedByRequester
	) {
		BuddyProfileDraft profile = record.profile();
		return new BuddyProfileView(
			record.profileId(),
			profile.profileImageUrl(),
			profile.nickname(),
			profile.nationality(),
			profile.availableLanguages(),
			profile.koreanLevel(),
			profile.bio(),
			profile.buddyStyles(),
			socialLinks,
			profile.profilePublic(),
			profile.snsPublic(),
			profile.allowsMessages(),
			canMessage,
			blockedByRequester,
			record.updatedAt());
	}
}
