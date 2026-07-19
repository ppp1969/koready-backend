package koready_backend.location.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import koready_backend.location.application.exception.UserLocationNotFoundException;
import koready_backend.location.application.exception.UserLocationUserUnavailableException;
import koready_backend.location.application.exception.InvalidLocationSearchTokenException;
import koready_backend.location.application.port.LocationSearchTokenCodec;
import koready_backend.location.application.port.LocationSearchTokenCodec.TokenPayload;
import koready_backend.location.application.port.UserLocationRepository;
import koready_backend.location.application.port.UserLocationRepository.NewLocation;
import koready_backend.location.application.port.UserLocationRepository.UserAccount;
import koready_backend.location.application.port.UserLocationRepository.UserLocationRecord;
import koready_backend.location.domain.LocationSearchCandidate;
import koready_backend.location.domain.LocationSearchResultType;
import koready_backend.place.domain.ServiceRegionCode;

class UserLocationServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-19T07:00:00Z");
	private static final Instant CREATED_AT = Instant.parse("2026-07-18T07:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));

	private final UserLocationRepository repository = mock(UserLocationRepository.class);
	private final LocationSearchTokenCodec tokenCodec = mock(LocationSearchTokenCodec.class);
	private final UserLocationService service = new UserLocationService(
		repository, tokenCodec, CLOCK);

	@Test
	void createsTheFirstLocationAsDefaultFromTheVerifiedToken() {
		when(repository.findActiveUserForUpdate("usr_emma"))
			.thenReturn(Optional.of(new UserAccount(7L, null)));
		when(tokenCodec.verify("locsrch_valid")).thenReturn(tokenPayload());
		when(repository.create(eq(7L), any(NewLocation.class), eq(NOW)))
			.thenReturn(location(101L, "학교"));

		UserLocationService.Location result = service.create(
			"usr_emma",
			new UserLocationService.CreateCommand(
				"locsrch_valid", "  학교  ", false));

		ArgumentCaptor<NewLocation> location = ArgumentCaptor.forClass(NewLocation.class);
		verify(repository).create(org.mockito.ArgumentMatchers.eq(7L), location.capture(),
			org.mockito.ArgumentMatchers.eq(NOW));
		assertEquals("서울시청", location.getValue().displayName());
		assertEquals("학교", location.getValue().customLabel());
		assertEquals("KAKAO", location.getValue().provider());
		assertEquals(37.5666, location.getValue().latitude());
		assertEquals(ServiceRegionCode.SEOUL, location.getValue().serviceRegionCode());
		verify(repository).updateDefaultLocation(7L, 101L, NOW);
		assertTrue(result.isDefault());
	}

	@Test
	void leavesTheExistingDefaultUnchangedWhenNotRequested() {
		when(repository.findActiveUserForUpdate("usr_emma"))
			.thenReturn(Optional.of(new UserAccount(7L, 100L)));
		when(tokenCodec.verify("locsrch_valid")).thenReturn(tokenPayload());
		when(repository.create(eq(7L), any(NewLocation.class), eq(NOW)))
			.thenReturn(location(101L, null));

		UserLocationService.Location result = service.create(
			"usr_emma",
			new UserLocationService.CreateCommand("locsrch_valid", null, false));

		verify(repository, never()).updateDefaultLocation(anyLong(),
			any(Long.class), any(Instant.class));
		assertFalse(result.isDefault());
	}

	@Test
	void replacesTheExistingDefaultWhenRequested() {
		when(repository.findActiveUserForUpdate("usr_emma"))
			.thenReturn(Optional.of(new UserAccount(7L, 100L)));
		when(tokenCodec.verify("locsrch_valid")).thenReturn(tokenPayload());
		when(repository.create(eq(7L), any(NewLocation.class), eq(NOW)))
			.thenReturn(location(101L, null));

		UserLocationService.Location result = service.create(
			"usr_emma",
			new UserLocationService.CreateCommand("locsrch_valid", null, true));

		verify(repository).updateDefaultLocation(7L, 101L, NOW);
		assertTrue(result.isDefault());
	}

	@Test
	void doesNotPersistWhenSearchTokenVerificationFails() {
		when(repository.findActiveUserForUpdate("usr_emma"))
			.thenReturn(Optional.of(new UserAccount(7L, 100L)));
		when(tokenCodec.verify("locsrch_tampered"))
			.thenThrow(new InvalidLocationSearchTokenException());

		assertThrows(InvalidLocationSearchTokenException.class,
			() -> service.create(
				"usr_emma",
				new UserLocationService.CreateCommand(
					"locsrch_tampered", null, true)));

		verify(repository, never()).create(anyLong(), any(NewLocation.class), any());
		verify(repository, never()).updateDefaultLocation(
			anyLong(), any(Long.class), any(Instant.class));
	}

	@Test
	void listsCompleteLocationsWithTheDefaultFirst() {
		UserLocationRecord newest = location(102L, "집");
		UserLocationRecord defaultLocation = location(101L, "학교");
		when(repository.findActiveUser("usr_emma"))
			.thenReturn(Optional.of(new UserAccount(7L, 101L)));
		when(repository.findAllCompleteActive(7L, 101L))
			.thenReturn(List.of(defaultLocation, newest));

		UserLocationService.LocationList result = service.getAll("usr_emma");

		assertEquals(List.of(101L, 102L), result.items().stream()
			.map(UserLocationService.Location::locationId)
			.toList());
		assertTrue(result.items().getFirst().isDefault());
		assertFalse(result.items().getLast().isDefault());
	}

	@Test
	void changesTheDefaultOnlyToAnOwnedActiveLocation() {
		when(repository.findActiveUserForUpdate("usr_emma"))
			.thenReturn(Optional.of(new UserAccount(7L, 100L)));
		when(repository.findCompleteActive(7L, 101L))
			.thenReturn(Optional.of(location(101L, "학교")));

		UserLocationService.Location result = service.setDefault("usr_emma", 101L);

		verify(repository).updateDefaultLocation(7L, 101L, NOW);
		assertEquals(101L, result.locationId());
		assertTrue(result.isDefault());
	}

	@Test
	void deletingTheDefaultPromotesTheNewestRemainingLocation() {
		when(repository.findActiveUserForUpdate("usr_emma"))
			.thenReturn(Optional.of(new UserAccount(7L, 101L)));
		when(repository.findCompleteActive(7L, 101L))
			.thenReturn(Optional.of(location(101L, "학교")));
		when(repository.findNewestCompleteActiveExcluding(7L, 101L))
			.thenReturn(Optional.of(location(99L, "집")));

		service.delete("usr_emma", 101L);

		verify(repository).updateDefaultLocation(7L, 99L, NOW);
		verify(repository).softDelete(7L, 101L, NOW);
	}

	@Test
	void deletingTheOnlyLocationClearsTheDefault() {
		when(repository.findActiveUserForUpdate("usr_emma"))
			.thenReturn(Optional.of(new UserAccount(7L, 101L)));
		when(repository.findCompleteActive(7L, 101L))
			.thenReturn(Optional.of(location(101L, "학교")));
		when(repository.findNewestCompleteActiveExcluding(7L, 101L))
			.thenReturn(Optional.empty());

		service.delete("usr_emma", 101L);

		verify(repository).updateDefaultLocation(7L, null, NOW);
		verify(repository).softDelete(7L, 101L, NOW);
	}

	@Test
	void rejectsMissingUsersAndLocations() {
		when(repository.findActiveUser("usr_missing")).thenReturn(Optional.empty());
		assertThrows(UserLocationUserUnavailableException.class,
			() -> service.getAll("usr_missing"));

		when(repository.findActiveUserForUpdate("usr_emma"))
			.thenReturn(Optional.of(new UserAccount(7L, 100L)));
		when(repository.findCompleteActive(7L, 999L)).thenReturn(Optional.empty());
		assertThrows(UserLocationNotFoundException.class,
			() -> service.setDefault("usr_emma", 999L));
		assertThrows(UserLocationNotFoundException.class,
			() -> service.delete("usr_emma", 999L));
	}

	@Test
	void rejectsInvalidCommandsBeforeTokenOrDatabaseAccess() {
		assertThrows(IllegalArgumentException.class,
			() -> service.create("usr_emma",
				new UserLocationService.CreateCommand(" ", null, false)));
		assertThrows(IllegalArgumentException.class,
			() -> service.create("usr_emma",
				new UserLocationService.CreateCommand("locsrch_valid", "x".repeat(31), false)));
		assertThrows(IllegalArgumentException.class,
			() -> service.setDefault("usr_emma", 0L));
		assertThrows(IllegalArgumentException.class,
			() -> service.delete("usr_emma", -1L));
		verifyNoInteractions(tokenCodec, repository);
	}

	private static TokenPayload tokenPayload() {
		return new TokenPayload(
			new LocationSearchCandidate(
				LocationSearchResultType.PLACE,
				"kakao-100",
				"서울시청",
				"서울특별시 중구 세종대로 110",
				"서울특별시 중구 태평로1가 31",
				37.5666,
				126.9784,
				"서울특별시",
				"중구",
				"태평로1가"),
			ServiceRegionCode.SEOUL,
			NOW.plusSeconds(600));
	}

	private static UserLocationRecord location(long id, String customLabel) {
		return new UserLocationRecord(
			id,
			7L,
			"서울시청",
			customLabel,
			"KAKAO",
			"kakao-100",
			"서울특별시 중구 세종대로 110",
			"서울특별시 중구 태평로1가 31",
			37.5666,
			126.9784,
			"서울특별시",
			"중구",
			"태평로1가",
			ServiceRegionCode.SEOUL,
			CREATED_AT.plusSeconds(id));
	}
}
