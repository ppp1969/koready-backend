package koready_backend.buddy.application.port;

import java.time.Instant;
import java.util.List;
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

	List<ThreadSummaryRow> findThreads(ThreadListCriteria criteria);

	long countUnreadMessages(long receiverProfileId);

	Optional<ThreadContext> findThreadContext(
		String threadPublicId,
		long requesterProfileId,
		String language
	);

	List<StoredMessage> findMessages(MessagePageCriteria criteria);

	Instant markRead(
		MessageThread thread,
		long receiverProfileId,
		Instant readAt
	);

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

	record ThreadListCriteria(
		long requesterProfileId,
		String language,
		ThreadListCursor cursor,
		int limit
	) {
	}

	record ThreadListCursor(Instant updatedAt, long threadDatabaseId) {
	}

	record ThreadSummaryRow(
		long threadDatabaseId,
		String threadPublicId,
		Instant updatedAt,
		PlaceSnapshot place,
		MessageProfile otherProfile,
		String latestContent,
		Instant lastSentAt,
		long unreadCount,
		boolean blocked
	) {
	}

	record ThreadContext(
		MessageThread thread,
		PlaceSnapshot place,
		MessageProfile otherProfile,
		boolean blockedByRequester,
		boolean blockedByTarget
	) {
		public boolean blocked() {
			return blockedByRequester || blockedByTarget;
		}
	}

	record MessagePageCriteria(
		long threadDatabaseId,
		String threadPublicId,
		long viewerProfileId,
		Long beforeMessageId,
		int limit
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
