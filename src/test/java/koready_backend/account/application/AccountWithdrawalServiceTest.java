package koready_backend.account.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import koready_backend.account.application.port.AccountWithdrawalRepository;
import koready_backend.account.domain.AccountStatus;
import koready_backend.user.application.exception.UserUnavailableException;

class AccountWithdrawalServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-02T06:00:00Z");
	private final AccountWithdrawalRepository repository = mock(AccountWithdrawalRepository.class);
	private final AccountAssetDeletionPort assets = mock(AccountAssetDeletionPort.class);
	private final AccountWithdrawalService service = new AccountWithdrawalService(
		repository, assets, Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void requestsWithdrawalWithASevenDayGracePeriod() {
		when(repository.request("usr_me", NOW, NOW.plusSeconds(7 * 86400L)))
			.thenReturn(Optional.of(state(AccountStatus.WITHDRAWAL_PENDING)));

		var result = service.request("usr_me");

		assertEquals(AccountStatus.WITHDRAWAL_PENDING, result.status());
		assertEquals(NOW.plusSeconds(7 * 86400L), result.scheduledFor());
	}

	@Test
	void cancelsWithinTheGracePeriod() {
		when(repository.cancel("usr_me", NOW))
			.thenReturn(Optional.of(state(AccountStatus.ACTIVE)));

		assertEquals(AccountStatus.ACTIVE, service.cancel("usr_me").status());
	}

	@Test
	void rejectsMissingUsers() {
		when(repository.request("missing", NOW, NOW.plusSeconds(7 * 86400L)))
			.thenReturn(Optional.empty());
		assertThrows(UserUnavailableException.class, () -> service.request("missing"));
	}

	@Test
	void confirmsDueAccountsAndSchedulesMessagePurgeThirtyDaysLater() {
		when(repository.findDueForConfirmation(NOW, 20)).thenReturn(List.of(7L));
		when(repository.findProfileImageKeys(7L)).thenReturn(List.of("profiles/a.jpg"));

		service.processDueConfirmations();

		verify(assets).delete("profiles/a.jpg");
		verify(repository).confirm(7L, NOW, NOW.plusSeconds(30 * 86400L));
	}

	private static AccountWithdrawalRepository.WithdrawalState state(AccountStatus status) {
		return new AccountWithdrawalRepository.WithdrawalState(
			7L, status, status == AccountStatus.ACTIVE ? null : NOW,
			status == AccountStatus.ACTIVE ? null : NOW.plusSeconds(7 * 86400L), null, null);
	}
}
