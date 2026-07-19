package koready_backend.buddy.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.buddy.application.exception.BuddyProfileNotFoundException;
import koready_backend.buddy.application.exception.BuddyUserUnavailableException;
import koready_backend.buddy.application.model.BuddyProfileView;
import koready_backend.buddy.application.port.BuddyBlockRepository;
import koready_backend.buddy.application.port.BuddyBlockRepository.BlockRelationship;
import koready_backend.buddy.application.port.BuddyProfileRepository;
import koready_backend.buddy.application.port.BuddyProfileRepository.BuddyProfileRecord;

@Service
public class BuddyPublicProfileService {

	private final BuddyProfileRepository profileRepository;
	private final BuddyBlockRepository blockRepository;

	public BuddyPublicProfileService(
		BuddyProfileRepository profileRepository,
		BuddyBlockRepository blockRepository
	) {
		this.profileRepository = profileRepository;
		this.blockRepository = blockRepository;
	}

	@Transactional(readOnly = true)
	public BuddyProfileView getProfile(
		String requesterPublicId,
		long profileId
	) {
		if (profileId <= 0) {
			throw new IllegalArgumentException("Buddy profile ID must be positive");
		}
		long requesterUserId = profileRepository.findActiveUserId(requesterPublicId)
			.orElseThrow(BuddyUserUnavailableException::new);
		BuddyProfileRecord target = profileRepository.findActiveById(profileId)
			.orElseThrow(() -> new BuddyProfileNotFoundException(profileId));
		boolean owner = requesterUserId == target.userId();
		if (!owner && !target.profile().profilePublic()) {
			throw new BuddyProfileNotFoundException(profileId);
		}

		if (owner) {
			return BuddyProfileViews.forPublic(target, false, false);
		}

		BlockRelationship relationship = blockRepository.relationship(
			requesterUserId, target.userId());
		if (relationship.blockedByTarget()) {
			throw new BuddyProfileNotFoundException(profileId);
		}
		boolean canMessage = target.profile().allowsMessages()
			&& !relationship.blockedByRequester()
			&& profileRepository.findByUserId(requesterUserId).isPresent();
		return BuddyProfileViews.forPublic(
			target, canMessage, relationship.blockedByRequester());
	}
}
