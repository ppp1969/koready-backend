package koready_backend.buddy.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.buddy.application.exception.BuddyProfileNotFoundException;
import koready_backend.buddy.application.exception.BuddyProfileRequiredException;
import koready_backend.buddy.application.exception.BuddyUserUnavailableException;
import koready_backend.buddy.application.exception.MessageIdempotencyConflictException;
import koready_backend.buddy.application.exception.MessageNotAllowedException;
import koready_backend.buddy.application.exception.MessagePlaceNotFoundException;
import koready_backend.buddy.application.exception.MessageThreadNotFoundException;
import koready_backend.buddy.application.port.BuddyBlockRepository;
import koready_backend.buddy.application.port.BuddyBlockRepository.BlockRelationship;
import koready_backend.buddy.application.port.BuddyMessageRepository;
import koready_backend.buddy.application.port.BuddyMessageRepository.ActiveUser;
import koready_backend.buddy.application.port.BuddyMessageRepository.IdempotencyClaim;
import koready_backend.buddy.application.port.BuddyMessageRepository.MessageProfile;
import koready_backend.buddy.application.port.BuddyMessageRepository.MessageThread;
import koready_backend.buddy.application.port.BuddyMessageRepository.PlaceSnapshot;
import koready_backend.buddy.application.port.BuddyMessageRepository.StoredMessage;
import koready_backend.buddy.application.port.ThreadIdGenerator;

@Service
public class BuddyMessageService {

	private static final int MAX_CONTENT_CODE_POINTS = 1_000;
	private static final int MIN_IDEMPOTENCY_KEY_LENGTH = 8;
	private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	private final BuddyMessageRepository repository;
	private final BuddyBlockRepository blockRepository;
	private final ThreadIdGenerator threadIdGenerator;
	private final Clock clock;

	@Autowired
	public BuddyMessageService(
		BuddyMessageRepository repository,
		BuddyBlockRepository blockRepository,
		ThreadIdGenerator threadIdGenerator
	) {
		this(repository, blockRepository, threadIdGenerator, Clock.system(SEOUL_ZONE));
	}

	BuddyMessageService(
		BuddyMessageRepository repository,
		BuddyBlockRepository blockRepository,
		ThreadIdGenerator threadIdGenerator,
		Clock clock
	) {
		this.repository = repository;
		this.blockRepository = blockRepository;
		this.threadIdGenerator = threadIdGenerator;
		this.clock = clock;
	}

	@Transactional
	public ThreadResult createThread(
		String userPublicId,
		String idempotencyKey,
		CreateThreadCommand command
	) {
		if (command == null || command.receiverProfileId() <= 0 || command.placeId() <= 0) {
			throw new IllegalArgumentException("Profile and place IDs must be positive.");
		}
		String content = normalizeContent(command.content());
		String key = normalizeIdempotencyKey(idempotencyKey);
		Sender sender = resolveSender(userPublicId);
		String hash = requestHash(
			"CREATE_THREAD",
			Long.toString(command.receiverProfileId()),
			Long.toString(command.placeId()),
			content);
		IdempotencyClaim claim = repository.claimIdempotency(
			sender.profile().profileId(), key, hash, clock.instant());
		if (claim.status() == BuddyMessageRepository.IdempotencyStatus.CONFLICT) {
			throw new MessageIdempotencyConflictException();
		}
		MessageProfile receiver = repository.findActiveProfile(command.receiverProfileId())
			.orElseThrow(() -> new BuddyProfileNotFoundException(
				command.receiverProfileId()));
		if (claim.status() == BuddyMessageRepository.IdempotencyStatus.REPLAY) {
			PlaceSnapshot place = findPlace(command.placeId(), sender.user());
			return threadResult(
				replayedMessage(claim), place, receiver, true);
		}
		ensureMessagingAllowed(sender.profile(), receiver);
		PlaceSnapshot place = findPlace(command.placeId(), sender.user());

		Instant sentAt = clock.instant();
		MessageThread thread = repository.findOrCreateThread(
			command.placeId(),
			sender.profile().profileId(),
			receiver.profileId(),
			threadIdGenerator.nextId(),
			sentAt);
		StoredMessage message = repository.appendMessage(
			thread,
			sender.profile().profileId(),
			receiver.profileId(),
			content,
			sentAt);
		repository.completeIdempotency(
			sender.profile().profileId(), key, message.messageId());
		return threadResult(message, place, receiver, true);
	}

	@Transactional
	public MessageResult reply(
		String userPublicId,
		String threadPublicId,
		String idempotencyKey,
		String requestedContent
	) {
		String threadId = normalizeThreadId(threadPublicId);
		String content = normalizeContent(requestedContent);
		String key = normalizeIdempotencyKey(idempotencyKey);
		Sender sender = resolveSender(userPublicId);
		String hash = requestHash("REPLY", threadId, content);
		IdempotencyClaim claim = repository.claimIdempotency(
			sender.profile().profileId(), key, hash, clock.instant());
		if (claim.status() == BuddyMessageRepository.IdempotencyStatus.CONFLICT) {
			throw new MessageIdempotencyConflictException();
		}
		if (claim.status() == BuddyMessageRepository.IdempotencyStatus.REPLAY) {
			return MessageResult.from(replayedMessage(claim));
		}
		MessageThread thread = repository.findParticipantThread(
			threadId, sender.profile().profileId())
			.orElseThrow(MessageThreadNotFoundException::new);
		long receiverProfileId = thread.otherProfileId(sender.profile().profileId());
		MessageProfile receiver = repository.findActiveProfile(receiverProfileId)
			.orElseThrow(MessageNotAllowedException::new);
		ensureMessagingAllowed(sender.profile(), receiver);

		StoredMessage message = repository.appendMessage(
			thread,
			sender.profile().profileId(),
			receiver.profileId(),
			content,
			clock.instant());
		repository.completeIdempotency(
			sender.profile().profileId(), key, message.messageId());
		return MessageResult.from(message);
	}

	private Sender resolveSender(String userPublicId) {
		ActiveUser user = repository.findActiveUser(userPublicId)
			.orElseThrow(BuddyUserUnavailableException::new);
		MessageProfile profile = repository.findProfileByUserId(user.userId())
			.orElseThrow(BuddyProfileRequiredException::new);
		return new Sender(user, profile);
	}

	private PlaceSnapshot findPlace(long placeId, ActiveUser user) {
		return repository.findActivePlace(placeId, user.preferredLanguage())
			.orElseThrow(() -> new MessagePlaceNotFoundException(placeId));
	}

	private void ensureMessagingAllowed(
		MessageProfile sender,
		MessageProfile receiver
	) {
		if (sender.profileId() == receiver.profileId()
			|| !receiver.profilePublic()
			|| !receiver.allowsMessages()) {
			throw new MessageNotAllowedException();
		}
		BlockRelationship relationship = blockRepository.relationship(
			sender.userId(), receiver.userId());
		if (relationship.blockedByRequester() || relationship.blockedByTarget()) {
			throw new MessageNotAllowedException();
		}
	}

	private StoredMessage replayedMessage(IdempotencyClaim claim) {
		if (claim.existingMessageId() == null) {
			throw new IllegalStateException("Completed idempotency key has no message");
		}
		return repository.findMessage(claim.existingMessageId())
			.orElseThrow(() -> new IllegalStateException(
				"Idempotent message no longer exists"));
	}

	private static ThreadResult threadResult(
		StoredMessage message,
		PlaceSnapshot place,
		MessageProfile receiver,
		boolean canReply
	) {
		return new ThreadResult(
			message.threadPublicId(),
			new PlaceSummary(place.placeId(), place.title(), place.imageUrl()),
			new ProfileSummary(
				receiver.profileId(), receiver.nickname(), receiver.profileImageUrl()),
			List.of(MessageResult.from(message)),
			null,
			false,
			canReply);
	}

	private static String normalizeContent(String content) {
		if (content == null) {
			throw new IllegalArgumentException("Message content is required.");
		}
		String normalized = content.strip();
		int length = normalized.codePointCount(0, normalized.length());
		if (length < 1 || length > MAX_CONTENT_CODE_POINTS) {
			throw new IllegalArgumentException(
				"Message content must contain between 1 and 1,000 characters.");
		}
		return normalized;
	}

	private static String normalizeIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null
			|| idempotencyKey.length() < MIN_IDEMPOTENCY_KEY_LENGTH
			|| idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH
			|| idempotencyKey.chars().anyMatch(value -> value < 0x21 || value > 0x7e)) {
			throw new IllegalArgumentException(
				"Idempotency-Key must be 8 to 100 visible ASCII characters.");
		}
		return idempotencyKey;
	}

	private static String normalizeThreadId(String threadId) {
		if (threadId == null || threadId.isBlank() || threadId.length() > 64) {
			throw new IllegalArgumentException("Invalid message thread ID.");
		}
		return threadId;
	}

	private static String requestHash(String... components) {
		StringBuilder canonical = new StringBuilder();
		for (String component : components) {
			byte[] bytes = component.getBytes(StandardCharsets.UTF_8);
			canonical.append(bytes.length).append(':').append(component).append('|');
		}
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256")
				.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	public record CreateThreadCommand(
		long receiverProfileId,
		long placeId,
		String content
	) {
	}

	public record ThreadResult(
		String threadId,
		PlaceSummary place,
		ProfileSummary otherProfile,
		List<MessageResult> messages,
		String nextCursor,
		boolean hasMore,
		boolean canReply
	) {
	}

	public record PlaceSummary(long placeId, String title, String imageUrl) {
	}

	public record ProfileSummary(
		long profileId,
		String nickname,
		String profileImageUrl
	) {
	}

	public record MessageResult(
		long messageId,
		String threadId,
		long senderProfileId,
		long receiverProfileId,
		long placeId,
		String content,
		Instant sentAt,
		boolean read,
		Instant readAt
	) {
		static MessageResult from(StoredMessage message) {
			return new MessageResult(
				message.messageId(),
				message.threadPublicId(),
				message.senderProfileId(),
				message.receiverProfileId(),
				message.placeId(),
				message.content(),
				message.sentAt(),
				message.readAt() != null,
				message.readAt());
		}
	}

	private record Sender(ActiveUser user, MessageProfile profile) {
	}
}
