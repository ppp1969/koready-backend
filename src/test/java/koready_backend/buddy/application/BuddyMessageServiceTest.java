package koready_backend.buddy.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;

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

class BuddyMessageServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-19T08:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));
	private static final String KEY = "message-key-001";
	private static final ActiveUser USER = new ActiveUser(7L, "EN");
	private static final MessageProfile SENDER = new MessageProfile(
		50L, 7L, "Sender", null, false, true);
	private static final MessageProfile RECEIVER = new MessageProfile(
		51L, 8L, "Receiver", "https://example.com/receiver.jpg", true, true);
	private static final PlaceSnapshot PLACE = new PlaceSnapshot(
		1001L, "Gimbap Festival", "https://example.com/place.jpg");
	private static final MessageThread THREAD = new MessageThread(
		101L, "thread_test_001", 1001L, 50L, 51L);
	private static final StoredMessage MESSAGE = new StoredMessage(
		9001L,
		"thread_test_001",
		50L,
		51L,
		1001L,
		"Hello!",
		NOW,
		null);

	private final BuddyMessageRepository repository = mock(BuddyMessageRepository.class);
	private final BuddyBlockRepository blockRepository = mock(BuddyBlockRepository.class);
	private final ThreadIdGenerator threadIdGenerator = mock(ThreadIdGenerator.class);
	private final BuddyMessageService service = new BuddyMessageService(
		repository, blockRepository, threadIdGenerator, CLOCK);

	@Test
	void createsAThreadAndFirstMessageAtomically() {
		allowSenderAndReceiver();
		when(repository.findActivePlace(1001L, "EN")).thenReturn(Optional.of(PLACE));
		when(repository.claimIdempotency(eq(50L), eq(KEY), anyString(), eq(NOW)))
			.thenReturn(IdempotencyClaim.claimed());
		when(threadIdGenerator.nextId()).thenReturn("thread_new_001");
		when(repository.findOrCreateThread(
			1001L, 50L, 51L, "thread_new_001", NOW)).thenReturn(THREAD);
		when(repository.appendMessage(THREAD, 50L, 51L, "Hello!", NOW))
			.thenReturn(MESSAGE);

		BuddyMessageService.ThreadResult result = service.createThread(
			"usr_sender",
			KEY,
			new BuddyMessageService.CreateThreadCommand(51L, 1001L, "  Hello!  "));

		assertEquals("thread_test_001", result.threadId());
		assertEquals(PLACE.placeId(), result.place().placeId());
		assertEquals(RECEIVER.profileId(), result.otherProfile().profileId());
		assertEquals(1, result.messages().size());
		assertEquals("Hello!", result.messages().getFirst().content());
		assertEquals(false, result.hasMore());
		assertEquals(true, result.canReply());
		verify(repository).completeIdempotency(50L, KEY, 9001L);
	}

	@Test
	void replaysTheStoredMessageWithoutAppendingAnotherRow() {
		allowSenderAndReceiver();
		when(repository.findActivePlace(1001L, "EN")).thenReturn(Optional.of(PLACE));
		when(repository.claimIdempotency(eq(50L), eq(KEY), anyString(), eq(NOW)))
			.thenReturn(IdempotencyClaim.replay(9001L));
		when(repository.findMessage(9001L)).thenReturn(Optional.of(MESSAGE));

		BuddyMessageService.ThreadResult result = service.createThread(
			"usr_sender",
			KEY,
			new BuddyMessageService.CreateThreadCommand(51L, 1001L, "Hello!"));

		assertEquals(9001L, result.messages().getFirst().messageId());
		verify(repository, never()).findOrCreateThread(
			1001L, 50L, 51L, "thread_new_001", NOW);
		verify(repository, never()).appendMessage(
			THREAD, 50L, 51L, "Hello!", NOW);
		verify(repository, never()).completeIdempotency(50L, KEY, 9001L);
	}

	@Test
	void rejectsAnIdempotencyKeyReusedForAnotherRequest() {
		allowSenderAndReceiver();
		when(repository.findActivePlace(1001L, "EN")).thenReturn(Optional.of(PLACE));
		when(repository.claimIdempotency(eq(50L), eq(KEY), anyString(), eq(NOW)))
			.thenReturn(IdempotencyClaim.conflict());

		assertThrows(MessageIdempotencyConflictException.class,
			() -> service.createThread(
				"usr_sender",
				KEY,
				new BuddyMessageService.CreateThreadCommand(51L, 1001L, "Different")));
	}

	@Test
	void repliesOnlyAsAThreadParticipant() {
		allowSender();
		when(repository.findParticipantThread("thread_test_001", 50L))
			.thenReturn(Optional.of(THREAD));
		when(repository.findActiveProfile(51L)).thenReturn(Optional.of(RECEIVER));
		when(blockRepository.relationship(7L, 8L))
			.thenReturn(new BlockRelationship(false, false));
		when(repository.claimIdempotency(eq(50L), eq(KEY), anyString(), eq(NOW)))
			.thenReturn(IdempotencyClaim.claimed());
		when(repository.appendMessage(THREAD, 50L, 51L, "Reply", NOW))
			.thenReturn(new StoredMessage(
				9002L, "thread_test_001", 50L, 51L, 1001L, "Reply", NOW, null));

		BuddyMessageService.MessageResult result = service.reply(
			"usr_sender", "thread_test_001", KEY, " Reply ");

		assertEquals(9002L, result.messageId());
		assertEquals("Reply", result.content());
		verify(repository).completeIdempotency(50L, KEY, 9002L);
	}

	@Test
	void hidesUnknownAndNonParticipantThreads() {
		allowSender();
		when(repository.findParticipantThread("thread_hidden", 50L))
			.thenReturn(Optional.empty());

		assertThrows(MessageThreadNotFoundException.class,
			() -> service.reply("usr_sender", "thread_hidden", KEY, "Reply"));
	}

	@Test
	void rejectsMissingSenderAccountOrBuddyProfile() {
		when(repository.findActiveUser("usr_missing")).thenReturn(Optional.empty());
		when(repository.findActiveUser("usr_without_profile"))
			.thenReturn(Optional.of(USER));
		when(repository.findProfileByUserId(7L)).thenReturn(Optional.empty());

		assertThrows(BuddyUserUnavailableException.class,
			() -> service.createThread(
				"usr_missing", KEY,
				new BuddyMessageService.CreateThreadCommand(51L, 1001L, "Hello")));
		assertThrows(BuddyProfileRequiredException.class,
			() -> service.createThread(
				"usr_without_profile", KEY,
				new BuddyMessageService.CreateThreadCommand(51L, 1001L, "Hello")));
	}

	@Test
	void rejectsMissingProfilesAndInactivePlaces() {
		allowSender();
		when(repository.findActiveProfile(999L)).thenReturn(Optional.empty());

		assertThrows(BuddyProfileNotFoundException.class,
			() -> service.createThread(
				"usr_sender", KEY,
				new BuddyMessageService.CreateThreadCommand(999L, 1001L, "Hello")));

		when(repository.findActiveProfile(51L)).thenReturn(Optional.of(RECEIVER));
		when(blockRepository.relationship(7L, 8L))
			.thenReturn(new BlockRelationship(false, false));
		when(repository.findActivePlace(999L, "EN")).thenReturn(Optional.empty());

		assertThrows(MessagePlaceNotFoundException.class,
			() -> service.createThread(
				"usr_sender", KEY,
				new BuddyMessageService.CreateThreadCommand(51L, 999L, "Hello")));
	}

	@Test
	void rejectsSelfPrivateOptedOutAndBlockedTargets() {
		allowSender();
		MessageProfile self = new MessageProfile(50L, 7L, "Sender", null, true, true);
		MessageProfile privateProfile = new MessageProfile(
			51L, 8L, "Private", null, false, true);
		MessageProfile optedOut = new MessageProfile(
			51L, 8L, "Opted Out", null, true, false);

		for (MessageProfile target : new MessageProfile[] { self, privateProfile, optedOut }) {
			when(repository.findActiveProfile(target.profileId()))
				.thenReturn(Optional.of(target));
			assertThrows(MessageNotAllowedException.class,
				() -> service.createThread(
					"usr_sender", KEY,
					new BuddyMessageService.CreateThreadCommand(
						target.profileId(), 1001L, "Hello")));
		}

		when(repository.findActiveProfile(51L)).thenReturn(Optional.of(RECEIVER));
		when(blockRepository.relationship(7L, 8L))
			.thenReturn(new BlockRelationship(true, false));
		assertThrows(MessageNotAllowedException.class,
			() -> service.createThread(
				"usr_sender", KEY,
				new BuddyMessageService.CreateThreadCommand(51L, 1001L, "Hello")));

		when(blockRepository.relationship(7L, 8L))
			.thenReturn(new BlockRelationship(false, true));
		assertThrows(MessageNotAllowedException.class,
			() -> service.createThread(
				"usr_sender", KEY,
				new BuddyMessageService.CreateThreadCommand(51L, 1001L, "Hello")));
	}

	@Test
	void countsUnicodeCodePointsAndRejectsInvalidInputBeforeDatabaseAccess() {
		String oneThousandEmoji = "😀".repeat(1_000);
		String oneThousandAndOneEmoji = oneThousandEmoji + "😀";

		assertThrows(IllegalArgumentException.class,
			() -> service.createThread(
				"usr_sender", KEY,
				new BuddyMessageService.CreateThreadCommand(51L, 1001L, "   ")));
		assertThrows(IllegalArgumentException.class,
			() -> service.createThread(
				"usr_sender", KEY,
				new BuddyMessageService.CreateThreadCommand(
					51L, 1001L, oneThousandAndOneEmoji)));
		assertThrows(IllegalArgumentException.class,
			() -> service.createThread(
				"usr_sender", "short",
				new BuddyMessageService.CreateThreadCommand(51L, 1001L, "Hello")));
		assertThrows(IllegalArgumentException.class,
			() -> service.createThread(
				"usr_sender", KEY,
				new BuddyMessageService.CreateThreadCommand(0L, 1001L, "Hello")));

		verifyNoInteractions(repository, blockRepository, threadIdGenerator);

		allowSenderAndReceiver();
		when(repository.findActivePlace(1001L, "EN")).thenReturn(Optional.of(PLACE));
		when(repository.claimIdempotency(eq(50L), eq(KEY), anyString(), eq(NOW)))
			.thenReturn(IdempotencyClaim.claimed());
		when(threadIdGenerator.nextId()).thenReturn("thread_new_001");
		when(repository.findOrCreateThread(
			1001L, 50L, 51L, "thread_new_001", NOW)).thenReturn(THREAD);
		StoredMessage unicodeMessage = new StoredMessage(
			9003L, "thread_test_001", 50L, 51L, 1001L,
			oneThousandEmoji, NOW, null);
		when(repository.appendMessage(
			THREAD, 50L, 51L, oneThousandEmoji, NOW)).thenReturn(unicodeMessage);

		BuddyMessageService.ThreadResult result = service.createThread(
			"usr_sender", KEY,
			new BuddyMessageService.CreateThreadCommand(
				51L, 1001L, oneThousandEmoji));
		assertEquals(1_000, result.messages().getFirst().content()
			.codePointCount(0, result.messages().getFirst().content().length()));
	}

	private void allowSender() {
		when(repository.findActiveUser("usr_sender")).thenReturn(Optional.of(USER));
		when(repository.findProfileByUserId(7L)).thenReturn(Optional.of(SENDER));
		when(repository.claimIdempotency(eq(50L), eq(KEY), anyString(), eq(NOW)))
			.thenReturn(IdempotencyClaim.claimed());
	}

	private void allowSenderAndReceiver() {
		allowSender();
		when(repository.findActiveProfile(51L)).thenReturn(Optional.of(RECEIVER));
		when(blockRepository.relationship(7L, 8L))
			.thenReturn(new BlockRelationship(false, false));
	}
}
