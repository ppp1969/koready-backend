package koready_backend.account.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import koready_backend.account.domain.AccountStatus;

public interface AccountWithdrawalRepository {

	Optional<WithdrawalState> find(String publicId);

	Optional<WithdrawalState> request(String publicId, Instant requestedAt, Instant scheduledFor);

	Optional<WithdrawalState> cancel(String publicId, Instant cancelledAt);

	List<Long> findDueForConfirmation(Instant now, int limit);

	List<String> findProfileImageKeys(long userId);

	void confirm(long userId, Instant confirmedAt, Instant messagePurgeAt);

	List<Long> findDueForMessagePurge(Instant now, int limit);

	void purgeMessagesAndTombstone(long userId);

	record WithdrawalState(
		long userId,
		AccountStatus status,
		Instant requestedAt,
		Instant scheduledFor,
		Instant confirmedAt,
		Instant messagePurgeAt
	) {
	}
}
