package koready_backend.buddy.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.buddy.application.exception.BuddyUserUnavailableException;
import koready_backend.buddy.application.port.BuddyProfileRepository;
import koready_backend.buddy.application.port.BuddyProfileRepository.BuddyProfileRecord;
import koready_backend.buddy.domain.BuddyProfileDraft;
import koready_backend.buddy.domain.BuddySocialLink;
import koready_backend.buddy.domain.BuddyStyle;
import koready_backend.buddy.domain.KoreanLevel;
import koready_backend.place.domain.PlaceLanguage;

@Service
public class BuddyProfileService {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	private final BuddyProfileRepository repository;
	private final Clock clock;

	@Autowired
	public BuddyProfileService(BuddyProfileRepository repository) {
		this(repository, Clock.system(SEOUL_ZONE));
	}

	BuddyProfileService(BuddyProfileRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public MyProfileResult getMyProfile(String userPublicId) {
		long userId = repository.findActiveUserId(userPublicId)
			.orElseThrow(BuddyUserUnavailableException::new);
		return repository.findByUserId(userId)
			.map(record -> new MyProfileResult(true, view(record)))
			.orElseGet(() -> new MyProfileResult(false, null));
	}

	@Transactional
	public BuddyProfileView upsertMyProfile(String userPublicId, UpsertCommand command) {
		long userId = repository.findActiveUserIdForUpdate(userPublicId)
			.orElseThrow(BuddyUserUnavailableException::new);
		BuddyProfileDraft draft = new BuddyProfileDraft(
			command.profileImageUrl(),
			command.nickname(),
			command.nationality(),
			command.availableLanguages(),
			command.koreanLevel(),
			command.bio(),
			command.buddyStyles(),
			command.socialLinks(),
			command.profilePublic(),
			command.snsPublic(),
			command.allowsMessages());
		return view(repository.save(userId, draft, clock.instant()));
	}

	private static BuddyProfileView view(BuddyProfileRecord record) {
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
			profile.socialLinks(),
			profile.profilePublic(),
			profile.snsPublic(),
			profile.allowsMessages(),
			false,
			false,
			record.updatedAt());
	}

	public record UpsertCommand(
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
		boolean allowsMessages
	) {
	}

	public record MyProfileResult(
		boolean exists,
		BuddyProfileView profile
	) {
	}

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
}
