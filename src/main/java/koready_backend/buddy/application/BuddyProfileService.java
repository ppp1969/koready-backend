package koready_backend.buddy.application;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.buddy.application.exception.BuddyUserUnavailableException;
import koready_backend.buddy.application.model.BuddyProfileView;
import koready_backend.buddy.application.port.BuddyProfileRepository;
import koready_backend.buddy.domain.BuddyProfileDraft;
import koready_backend.buddy.domain.BuddySocialLink;
import koready_backend.buddy.domain.BuddyStyle;
import koready_backend.buddy.domain.KoreanLevel;
import koready_backend.buddy.domain.ProfileLanguage;
import koready_backend.place.domain.TravelStyle;

@Service
public class BuddyProfileService {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	private final BuddyProfileRepository repository;
	private final ProfileImageService profileImages;
	private final Clock clock;

	@Autowired
	public BuddyProfileService(
		BuddyProfileRepository repository,
		ProfileImageService profileImages
	) {
		this(repository, profileImages, Clock.system(SEOUL_ZONE));
	}

	BuddyProfileService(
		BuddyProfileRepository repository,
		ProfileImageService profileImages,
		Clock clock
	) {
		this.repository = repository;
		this.profileImages = profileImages;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public MyProfileResult getMyProfile(String userPublicId) {
		long userId = repository.findActiveUserId(userPublicId)
			.orElseThrow(BuddyUserUnavailableException::new);
		return repository.findByUserId(userId)
			.map(record -> new MyProfileResult(true, BuddyProfileViews.forOwner(record)))
			.orElseGet(() -> new MyProfileResult(false, null));
	}

	@Transactional
	public BuddyProfileView upsertMyProfile(String userPublicId, UpsertCommand command) {
		long userId = repository.findActiveUserIdForUpdate(userPublicId)
			.orElseThrow(BuddyUserUnavailableException::new);
		if (command.profileImageUrl() != null
			&& !profileImages.isReadyOwnedBy(userId, command.profileImageUrl())) {
			throw new IllegalArgumentException(
				"Profile image must be a completed upload owned by the user");
		}
		List<BuddyStyle> buddyStyles = command.buddyStyles() == null
			? repository.findByUserId(userId)
				.map(record -> record.profile().buddyStyles())
				.orElse(List.of())
			: command.buddyStyles();
		BuddyProfileDraft draft = new BuddyProfileDraft(
			command.profileImageUrl(),
			command.nickname(),
			command.nationality(),
			command.availableLanguages(),
			command.koreanLevel(),
			command.travelStyles(),
			command.bio(),
			buddyStyles,
			command.socialLinks(),
			command.profilePublic(),
			command.snsPublic(),
			command.allowsMessages());
		return BuddyProfileViews.forOwner(
			repository.save(userId, draft, clock.instant()));
	}

	public record UpsertCommand(
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

		public UpsertCommand(
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
	}

	public record MyProfileResult(
		boolean exists,
		BuddyProfileView profile
	) {
	}

}
