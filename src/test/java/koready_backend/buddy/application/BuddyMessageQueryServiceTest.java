package koready_backend.buddy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import koready_backend.buddy.application.exception.InvalidMessageCursorException;
import koready_backend.buddy.application.port.BuddyMessageRepository;
import koready_backend.buddy.application.port.BuddyMessageRepository.ActiveUser;
import koready_backend.buddy.application.port.BuddyMessageRepository.MessagePageCriteria;
import koready_backend.buddy.application.port.BuddyMessageRepository.MessageProfile;
import koready_backend.buddy.application.port.BuddyMessageRepository.MessageThread;
import koready_backend.buddy.application.port.BuddyMessageRepository.PlaceSnapshot;
import koready_backend.buddy.application.port.BuddyMessageRepository.StoredMessage;
import koready_backend.buddy.application.port.BuddyMessageRepository.ThreadContext;
import koready_backend.buddy.application.port.BuddyMessageRepository.ThreadListCriteria;
import koready_backend.buddy.application.port.BuddyMessageRepository.ThreadSummaryRow;

@ExtendWith(MockitoExtension.class)
class BuddyMessageQueryServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-19T09:00:00Z");
	private static final MessageProfile VIEWER = new MessageProfile(
		50L, 5L, "Viewer", null, true, true);
	private static final MessageProfile OTHER = new MessageProfile(
		51L, 6L, "Other", "https://example.com/profile.jpg", true, true);
	private static final PlaceSnapshot PLACE = new PlaceSnapshot(
		1001L, "Gimbap Festival", "https://example.com/place.jpg");

	@Mock
	private BuddyMessageRepository repository;

	private BuddyMessageQueryService service;

	@BeforeEach
	void setUp() {
		service = new BuddyMessageQueryService(
			repository, Clock.fixed(NOW, ZoneOffset.UTC));
		when(repository.findActiveUser("usr_viewer"))
			.thenReturn(Optional.of(new ActiveUser(5L, "EN")));
		when(repository.findProfileByUserId(5L)).thenReturn(Optional.of(VIEWER));
	}

	@Test
	void listsNewestThreadsWithAnOpaqueCursorAndUnicodeSafePreview() {
		String longPreview = "가".repeat(98) + "🙂🙂🙂";
		when(repository.findThreads(any())).thenReturn(List.of(
			row(13L, "thread_003", NOW, longPreview, 2L, false),
			row(12L, "thread_002", NOW.minusSeconds(60), "Second", 0L, true),
			row(11L, "thread_001", NOW.minusSeconds(120), "Older", 1L, false)));
		when(repository.countUnreadMessages(50L)).thenReturn(3L);

		BuddyMessageQueryService.ThreadListResult result =
			service.getThreads("usr_viewer", null, 2);

		assertThat(result.items()).extracting(BuddyMessageQueryService.ThreadSummary::threadId)
			.containsExactly("thread_003", "thread_002");
		assertThat(result.items().getFirst().preview().codePointCount(
			0, result.items().getFirst().preview().length())).isEqualTo(100);
		assertThat(result.items().getFirst().preview()).endsWith("...");
		assertThat(result.items().get(1).blocked()).isTrue();
		assertThat(result.items().get(1).canReply()).isFalse();
		assertThat(result.hasMore()).isTrue();
		assertThat(result.nextCursor()).isNotBlank();
		assertThat(result.unreadTotal()).isEqualTo(3L);

		ArgumentCaptor<ThreadListCriteria> criteria =
			ArgumentCaptor.forClass(ThreadListCriteria.class);
		verify(repository).findThreads(criteria.capture());
		assertThat(criteria.getValue().language()).isEqualTo("EN");
		assertThat(criteria.getValue().limit()).isEqualTo(3);
	}

	@Test
	void returnsMessagesOldestFirstAndMakesBlockedThreadsReadOnly() {
		MessageThread thread = new MessageThread(12L, "thread_002", 1001L, 50L, 51L);
		when(repository.findThreadContext("thread_002", 50L, "EN"))
			.thenReturn(Optional.of(new ThreadContext(thread, PLACE, OTHER, true, false)));
		when(repository.findMessages(any()))
			.thenReturn(List.of(
				message(103L, "Newest", NOW),
				message(102L, "Middle", NOW.minusSeconds(1)),
				message(101L, "Oldest", NOW.minusSeconds(2))))
			.thenReturn(List.of(message(101L, "Oldest", NOW.minusSeconds(2))));

		BuddyMessageService.ThreadResult result =
			service.getThread("usr_viewer", "thread_002", null, 2);

		assertThat(result.messages()).extracting(BuddyMessageService.MessageResult::messageId)
			.containsExactly(102L, 103L);
		assertThat(result.hasMore()).isTrue();
		assertThat(result.nextCursor()).isNotBlank();
		assertThat(result.canReply()).isFalse();
		BuddyMessageService.ThreadResult older = service.getThread(
			"usr_viewer", "thread_002", result.nextCursor(), 2);
		assertThat(older.messages()).extracting(BuddyMessageService.MessageResult::messageId)
			.containsExactly(101L);

		ArgumentCaptor<MessagePageCriteria> criteria =
			ArgumentCaptor.forClass(MessagePageCriteria.class);
		verify(repository, times(2)).findMessages(criteria.capture());
		assertThat(criteria.getAllValues().getFirst().limit()).isEqualTo(3);
		assertThat(criteria.getAllValues().getLast().beforeMessageId()).isEqualTo(102L);
	}

	@Test
	void marksOnlyIncomingMessagesReadAndReturnsTheGlobalUnreadCount() {
		MessageThread thread = new MessageThread(12L, "thread_002", 1001L, 50L, 51L);
		when(repository.findParticipantThread("thread_002", 50L))
			.thenReturn(Optional.of(thread));
		when(repository.findThreadContext("thread_002", 50L, "EN"))
			.thenReturn(Optional.of(new ThreadContext(thread, PLACE, OTHER, false, false)));
		when(repository.markRead(thread, 50L, NOW)).thenReturn(NOW);
		when(repository.countUnreadMessages(50L)).thenReturn(4L);

		BuddyMessageQueryService.ReadResult result =
			service.markRead("usr_viewer", "thread_002");

		assertThat(result.threadId()).isEqualTo("thread_002");
		assertThat(result.readAt()).isEqualTo(NOW);
		assertThat(result.threadUnreadCount()).isZero();
		assertThat(result.unreadTotal()).isEqualTo(4L);
		verify(repository).markRead(thread, 50L, NOW);
	}

	@Test
	void rejectsMalformedAndCrossEndpointCursors() {
		when(repository.findThreads(any())).thenReturn(List.of(
			row(13L, "thread_003", NOW, "Hello", 0L, false),
			row(12L, "thread_002", NOW.minusSeconds(60), "Second", 0L, false)));
		when(repository.countUnreadMessages(50L)).thenReturn(0L);
		MessageThread thread = new MessageThread(12L, "thread_002", 1001L, 50L, 51L);
		when(repository.findThreadContext("thread_002", 50L, "EN"))
			.thenReturn(Optional.of(new ThreadContext(thread, PLACE, OTHER, false, false)));
		String threadListCursor = service.getThreads("usr_viewer", null, 1).nextCursor();

		assertThatThrownBy(() -> service.getThreads("usr_viewer", "not-base64!", 20))
			.isInstanceOf(InvalidMessageCursorException.class);
		assertThatThrownBy(() -> service.getThreads("usr_viewer", null, 0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.getThread(
			"usr_viewer", "thread_002", threadListCursor, 20))
			.isInstanceOf(InvalidMessageCursorException.class);
	}

	private static ThreadSummaryRow row(
		long databaseId,
		String publicId,
		Instant updatedAt,
		String content,
		long unreadCount,
		boolean blocked
	) {
		return new ThreadSummaryRow(
			databaseId,
			publicId,
			updatedAt,
			PLACE,
			OTHER,
			content,
			updatedAt,
			unreadCount,
			blocked);
	}

	private static StoredMessage message(long id, String content, Instant sentAt) {
		return new StoredMessage(
			id, "thread_002", 51L, 50L, 1001L, content, sentAt, null);
	}
}
