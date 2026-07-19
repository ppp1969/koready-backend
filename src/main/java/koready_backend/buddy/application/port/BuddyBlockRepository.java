package koready_backend.buddy.application.port;

import java.time.Instant;
import java.util.Optional;

public interface BuddyBlockRepository {

	Optional<Long> findActiveUserId(String publicId);

	Optional<Long> findActiveProfileOwnerId(long profileId);

	BlockRelationship relationship(long requesterUserId, long targetUserId);

	Instant block(long blockerUserId, long blockedUserId, Instant blockedAt);

	void unblock(long blockerUserId, long blockedUserId);

	record BlockRelationship(
		boolean blockedByRequester,
		boolean blockedByTarget
	) {
	}
}
