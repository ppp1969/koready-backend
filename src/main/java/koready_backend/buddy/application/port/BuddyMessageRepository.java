package koready_backend.buddy.application.port;

import java.time.Instant;
import java.util.Optional;

public interface BuddyMessageRepository {

	Optional<ActiveUser> findActiveUser(String publicId);

	Optional<MessageProfile> findProfileByUserId(long userId);

	Optional<MessageProfile> findActiveProfile(long profileId);

	Optional<PlaceSnapshot> findActivePlace(long placeId, String language);

	Optional<MessageThread> findParticipantThread(String threadPublicId, long profileId);

	IdempotencyClaim claimIdempotency(
		long senderProfileId,
		String idempotencyKey,
		String requestHash,
		Instant claimedAt
	);

	MessageThread findOrCreateThread(
		long placeId,
		long firstProfileId,
		long secondProfileId,
		String newPublicId,
		Instant createdAt
	);

	StoredMessage appendMessage(
		MessageThread thread,
		long senderProfileId,
		long receiverProfileId,
		String content,
		Instant sentAt
	);

	void completeIdempotency(
		long senderProfileId,
		String idempotencyKey,
		long messageId
	);

	Optional<StoredMessage> findMessage(long messageId);

	record ActiveUser(long userId, String preferredLanguage) {
	}

	record MessageProfile(
		long profileId,
		long userId,
		String nickname,
		String profileImageUrl,
		boolean profilePublic,
		boolean allowsMessages
	) {
	}

	record PlaceSnapshot(
		long placeId,
		String title,
		String imageUrl
	) {
	}

	record MessageThread(
		long databaseId,
		String publicId,
		long placeId,
		long profileLowId,
		long profileHighId
	) {
		public long otherProfileId(long profileId) {
			if (profileLowId == profileId) {
				return profileHighId;
			}
			if (profileHighId == profileId) {
				return profileLowId;
			}
			throw new IllegalArgumentException("Profile is not a thread participant");
		}
	}

	record StoredMessage(
		long messageId,
		String threadPublicId,
		long senderProfileId,
		long receiverProfileId,
		long placeId,
		String content,
		Instant sentAt,
		Instant readAt
	) {
	}

	record IdempotencyClaim(
		IdempotencyStatus status,
		Long existingMessageId
	) {
		public static IdempotencyClaim claimed() {
			return new IdempotencyClaim(IdempotencyStatus.CLAIMED, null);
		}

		public static IdempotencyClaim replay(long messageId) {
			return new IdempotencyClaim(IdempotencyStatus.REPLAY, messageId);
		}

		public static IdempotencyClaim conflict() {
			return new IdempotencyClaim(IdempotencyStatus.CONFLICT, null);
		}
	}

	enum IdempotencyStatus {
		CLAIMED,
		REPLAY,
		CONFLICT
	}
}
