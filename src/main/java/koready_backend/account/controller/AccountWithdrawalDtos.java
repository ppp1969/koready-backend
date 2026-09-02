package koready_backend.account.controller;

import java.time.Instant;
import koready_backend.account.application.port.AccountWithdrawalRepository.WithdrawalState;
import koready_backend.account.domain.AccountStatus;

final class AccountWithdrawalDtos {
	private AccountWithdrawalDtos() {}
	static Response from(WithdrawalState state) {
		return new Response(state.status(), state.requestedAt(), state.scheduledFor(), state.confirmedAt(), state.messagePurgeAt());
	}
	record Response(AccountStatus status, Instant requestedAt, Instant scheduledFor, Instant confirmedAt, Instant messagePurgeAt) {}
}
