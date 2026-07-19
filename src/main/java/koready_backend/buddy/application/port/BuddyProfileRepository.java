package koready_backend.buddy.application.port;

import java.time.Instant;
import java.util.Optional;

import koready_backend.buddy.domain.BuddyProfileDraft;

public interface BuddyProfileRepository {

	Optional<Long> findActiveUserId(String publicId);

	Optional<Long> findActiveUserIdForUpdate(String publicId);

	Optional<BuddyProfileRecord> findByUserId(long userId);

	Optional<BuddyProfileRecord> findActiveById(long profileId);

	BuddyProfileRecord save(long userId, BuddyProfileDraft profile, Instant updatedAt);

	record BuddyProfileRecord(
		long profileId,
		long userId,
		BuddyProfileDraft profile,
		Instant createdAt,
		Instant updatedAt
	) {
	}
}
