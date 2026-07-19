package koready_backend.buddy.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import koready_backend.buddy.application.exception.BuddyProfileNotFoundException;
import koready_backend.buddy.application.exception.BuddyUserUnavailableException;
import koready_backend.buddy.application.port.BuddyBlockRepository;

class BuddyBlockServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-19T04:00:00Z");
	private static final Instant FIRST_BLOCKED_AT =
		Instant.parse("2026-07-18T04:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));

	private final BuddyBlockRepository repository = mock(BuddyBlockRepository.class);
	private final BuddyBlockService service = new BuddyBlockService(repository, CLOCK);

	@Test
	void blocksATargetProfileAndReturnsThePersistedTimestamp() {
		when(repository.findActiveUserId("usr_blocker")).thenReturn(Optional.of(7L));
		when(repository.findActiveProfileOwnerId(51L)).thenReturn(Optional.of(8L));
		when(repository.block(7L, 8L, NOW)).thenReturn(FIRST_BLOCKED_AT);

		BuddyBlockService.BlockResult result = service.block("usr_blocker", 51L);

		assertEquals(51L, result.profileId());
		assertEquals(FIRST_BLOCKED_AT, result.blockedAt());
		verify(repository).block(7L, 8L, NOW);
	}

	@Test
	void unblocksIdempotentlyAfterResolvingTheTarget() {
		when(repository.findActiveUserId("usr_blocker")).thenReturn(Optional.of(7L));
		when(repository.findActiveProfileOwnerId(51L)).thenReturn(Optional.of(8L));

		service.unblock("usr_blocker", 51L);

		verify(repository).unblock(7L, 8L);
	}

	@Test
	void rejectsBlockingOrUnblockingOnesOwnProfile() {
		when(repository.findActiveUserId("usr_self")).thenReturn(Optional.of(7L));
		when(repository.findActiveProfileOwnerId(51L)).thenReturn(Optional.of(7L));

		assertThrows(IllegalArgumentException.class,
			() -> service.block("usr_self", 51L));
		assertThrows(IllegalArgumentException.class,
			() -> service.unblock("usr_self", 51L));

		verify(repository, times(2)).findActiveUserId("usr_self");
		verify(repository, times(2)).findActiveProfileOwnerId(51L);
		verifyNoMoreInteractions(repository);
	}

	@Test
	void rejectsMissingOrDeletedTargetProfiles() {
		when(repository.findActiveUserId("usr_blocker")).thenReturn(Optional.of(7L));
		when(repository.findActiveProfileOwnerId(999L)).thenReturn(Optional.empty());

		assertThrows(BuddyProfileNotFoundException.class,
			() -> service.block("usr_blocker", 999L));
		assertThrows(BuddyProfileNotFoundException.class,
			() -> service.unblock("usr_blocker", 999L));
	}

	@Test
	void rejectsADeletedOrMissingAuthenticatedUser() {
		when(repository.findActiveUserId("usr_missing")).thenReturn(Optional.empty());

		assertThrows(BuddyUserUnavailableException.class,
			() -> service.block("usr_missing", 51L));
		assertThrows(BuddyUserUnavailableException.class,
			() -> service.unblock("usr_missing", 51L));
	}

	@Test
	void rejectsNonPositiveProfileIdsBeforeRepositoryAccess() {
		assertThrows(IllegalArgumentException.class,
			() -> service.block("usr_blocker", 0L));
		assertThrows(IllegalArgumentException.class,
			() -> service.unblock("usr_blocker", -1L));
		verifyNoInteractions(repository);
	}

}
