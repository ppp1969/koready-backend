package koready_backend.buddy.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.buddy.application.exception.BuddyProfileRequiredException;
import koready_backend.buddy.application.exception.BuddyUserUnavailableException;
import koready_backend.buddy.application.exception.InvalidMessageCursorException;
import koready_backend.buddy.application.exception.MessageThreadNotFoundException;
import koready_backend.buddy.application.port.BuddyMessageRepository;
import koready_backend.buddy.application.port.BuddyMessageRepository.ActiveUser;
import koready_backend.buddy.application.port.BuddyMessageRepository.MessagePageCriteria;
import koready_backend.buddy.application.port.BuddyMessageRepository.MessageProfile;
import koready_backend.buddy.application.port.BuddyMessageRepository.MessageThread;
import koready_backend.buddy.application.port.BuddyMessageRepository.StoredMessage;
import koready_backend.buddy.application.port.BuddyMessageRepository.ThreadContext;
import koready_backend.buddy.application.port.BuddyMessageRepository.ThreadListCriteria;
import koready_backend.buddy.application.port.BuddyMessageRepository.ThreadListCursor;
import koready_backend.buddy.application.port.BuddyMessageRepository.ThreadSummaryRow;

@Service
public class BuddyMessageQueryService {

	private static final int MAX_CURSOR_LENGTH = 512;
	private static final int PREVIEW_CODE_POINTS = 100;
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	private final BuddyMessageRepository repository;
	private final Clock clock;

	@Autowired
	public BuddyMessageQueryService(BuddyMessageRepository repository) {
		this(repository, Clock.system(SEOUL_ZONE));
	}

	BuddyMessageQueryService(BuddyMessageRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public ThreadListResult getThreads(
		String userPublicId,
		String cursorToken,
		int size
	) {
		validatePageSize(size);
		Viewer viewer = resolveViewer(userPublicId);
		String fingerprint = fingerprint(
			"MESSAGE_THREADS", viewer.user().preferredLanguage());
		ThreadListCursor cursor = decodeThreadCursor(cursorToken, fingerprint);
		List<ThreadSummaryRow> rows = repository.findThreads(new ThreadListCriteria(
			viewer.profile().profileId(),
			viewer.user().preferredLanguage(),
			cursor,
			size + 1));
		boolean hasMore = rows.size() > size;
		List<ThreadSummaryRow> visible = rows.subList(0, Math.min(size, rows.size()));
		String nextCursor = null;
		if (hasMore && !visible.isEmpty()) {
			ThreadSummaryRow last = visible.getLast();
			nextCursor = encodeThreadCursor(fingerprint,
				new ThreadListCursor(last.updatedAt(), last.threadDatabaseId()));
		}
		List<ThreadSummary> items = visible.stream()
			.map(BuddyMessageQueryService::summary)
			.toList();
		return new ThreadListResult(
			items,
			nextCursor,
			hasMore,
			repository.countUnreadMessages(viewer.profile().profileId()));
	}

	@Transactional(readOnly = true)
	public BuddyMessageService.ThreadResult getThread(
		String userPublicId,
		String threadPublicId,
		String cursorToken,
		int size
	) {
		validatePageSize(size);
		String threadId = normalizeThreadId(threadPublicId);
		Viewer viewer = resolveViewer(userPublicId);
		ThreadContext context = repository.findThreadContext(
			threadId,
			viewer.profile().profileId(),
			viewer.user().preferredLanguage())
			.orElseThrow(MessageThreadNotFoundException::new);
		String fingerprint = fingerprint("MESSAGE_THREAD", threadId);
		Long beforeMessageId = decodeMessageCursor(cursorToken, fingerprint);
		List<StoredMessage> rows = repository.findMessages(new MessagePageCriteria(
			context.thread().databaseId(),
			threadId,
			viewer.profile().profileId(),
			beforeMessageId,
			size + 1));
		boolean hasMore = rows.size() > size;
		List<StoredMessage> visible = new ArrayList<>(
			rows.subList(0, Math.min(size, rows.size())));
		String nextCursor = null;
		if (hasMore && !visible.isEmpty()) {
			nextCursor = encodeMessageCursor(
				fingerprint, visible.getLast().messageId());
		}
		List<BuddyMessageService.MessageResult> messages = visible.reversed().stream()
			.map(BuddyMessageService.MessageResult::from)
			.toList();
		boolean canReply = !context.blocked()
			&& context.otherProfile().profilePublic()
			&& context.otherProfile().allowsMessages();
		return new BuddyMessageService.ThreadResult(
			context.thread().publicId(),
			new BuddyMessageService.PlaceSummary(
				context.place().placeId(),
				context.place().title(),
				context.place().imageUrl()),
			new BuddyMessageService.ProfileSummary(
				context.otherProfile().profileId(),
				context.otherProfile().nickname(),
				context.otherProfile().profileImageUrl()),
			messages,
			nextCursor,
			hasMore,
			canReply);
	}

	@Transactional
	public ReadResult markRead(String userPublicId, String threadPublicId) {
		String threadId = normalizeThreadId(threadPublicId);
		Viewer viewer = resolveViewer(userPublicId);
		MessageThread thread = repository.findParticipantThread(
			threadId, viewer.profile().profileId())
			.orElseThrow(MessageThreadNotFoundException::new);
		repository.findThreadContext(
			threadId,
			viewer.profile().profileId(),
			viewer.user().preferredLanguage())
			.orElseThrow(MessageThreadNotFoundException::new);
		Instant readAt = repository.markRead(
			thread, viewer.profile().profileId(), clock.instant());
		return new ReadResult(
			thread.publicId(),
			readAt,
			0,
			repository.countUnreadMessages(viewer.profile().profileId()));
	}

	private Viewer resolveViewer(String userPublicId) {
		ActiveUser user = repository.findActiveUser(userPublicId)
			.orElseThrow(BuddyUserUnavailableException::new);
		MessageProfile profile = repository.findProfileByUserId(user.userId())
			.orElseThrow(BuddyProfileRequiredException::new);
		return new Viewer(user, profile);
	}

	private static ThreadSummary summary(ThreadSummaryRow row) {
		return new ThreadSummary(
			row.threadPublicId(),
			new BuddyMessageService.PlaceSummary(
				row.place().placeId(), row.place().title(), row.place().imageUrl()),
			new BuddyMessageService.ProfileSummary(
				row.otherProfile().profileId(),
				row.otherProfile().nickname(),
				row.otherProfile().profileImageUrl()),
			preview(row.latestContent()),
			row.lastSentAt(),
			row.unreadCount(),
			row.blocked(),
			!row.blocked()
				&& row.otherProfile().profilePublic()
				&& row.otherProfile().allowsMessages());
	}

	private static void validatePageSize(int size) {
		if (size < 1 || size > 50) {
			throw new IllegalArgumentException("Page size must be between 1 and 50.");
		}
	}

	private static String preview(String content) {
		String normalized = content.strip().replaceAll("\\s+", " ");
		int count = normalized.codePointCount(0, normalized.length());
		if (count <= PREVIEW_CODE_POINTS) {
			return normalized;
		}
		int end = normalized.offsetByCodePoints(0, PREVIEW_CODE_POINTS - 3);
		return normalized.substring(0, end).stripTrailing() + "...";
	}

	private static String normalizeThreadId(String threadId) {
		if (threadId == null || threadId.isBlank() || threadId.length() > 64) {
			throw new IllegalArgumentException("Invalid message thread ID.");
		}
		return threadId;
	}

	private static String fingerprint(String... values) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (String value : values) {
				digest.update(value.getBytes(StandardCharsets.UTF_8));
				digest.update((byte) 0);
			}
			return HexFormat.of().formatHex(digest.digest(), 0, 12);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private static String encodeThreadCursor(
		String fingerprint,
		ThreadListCursor cursor
	) {
		return encode(String.join("\t",
			"1", "THREADS", fingerprint, cursor.updatedAt().toString(),
			Long.toString(cursor.threadDatabaseId())));
	}

	private static ThreadListCursor decodeThreadCursor(
		String token,
		String expectedFingerprint
	) {
		if (token == null || token.isBlank()) {
			return null;
		}
		String[] parts = decode(token);
		try {
			if (parts.length != 5
				|| !"1".equals(parts[0])
				|| !"THREADS".equals(parts[1])
				|| !expectedFingerprint.equals(parts[2])) {
				throw new InvalidMessageCursorException();
			}
			Instant updatedAt = Instant.parse(parts[3]);
			long threadId = Long.parseLong(parts[4]);
			if (threadId <= 0) {
				throw new InvalidMessageCursorException();
			}
			return new ThreadListCursor(updatedAt, threadId);
		} catch (InvalidMessageCursorException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new InvalidMessageCursorException();
		}
	}

	private static String encodeMessageCursor(String fingerprint, long messageId) {
		return encode(String.join("\t",
			"1", "MESSAGES", fingerprint, Long.toString(messageId)));
	}

	private static Long decodeMessageCursor(
		String token,
		String expectedFingerprint
	) {
		if (token == null || token.isBlank()) {
			return null;
		}
		String[] parts = decode(token);
		try {
			if (parts.length != 4
				|| !"1".equals(parts[0])
				|| !"MESSAGES".equals(parts[1])
				|| !expectedFingerprint.equals(parts[2])) {
				throw new InvalidMessageCursorException();
			}
			long messageId = Long.parseLong(parts[3]);
			if (messageId <= 0) {
				throw new InvalidMessageCursorException();
			}
			return messageId;
		} catch (InvalidMessageCursorException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new InvalidMessageCursorException();
		}
	}

	private static String encode(String payload) {
		return Base64.getUrlEncoder().withoutPadding()
			.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
	}

	private static String[] decode(String token) {
		if (token.length() > MAX_CURSOR_LENGTH) {
			throw new InvalidMessageCursorException();
		}
		try {
			return new String(
				Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8)
				.split("\t", -1);
		} catch (RuntimeException exception) {
			throw new InvalidMessageCursorException();
		}
	}

	public record ThreadListResult(
		List<ThreadSummary> items,
		String nextCursor,
		boolean hasMore,
		long unreadTotal
	) {
	}

	public record ThreadSummary(
		String threadId,
		BuddyMessageService.PlaceSummary place,
		BuddyMessageService.ProfileSummary otherProfile,
		String preview,
		Instant lastSentAt,
		long unreadCount,
		boolean blocked,
		boolean canReply
	) {
	}

	public record ReadResult(
		String threadId,
		Instant readAt,
		long threadUnreadCount,
		long unreadTotal
	) {
	}

	private record Viewer(ActiveUser user, MessageProfile profile) {
	}
}
