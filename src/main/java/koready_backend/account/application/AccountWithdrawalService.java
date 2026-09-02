package koready_backend.account.application;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.account.application.port.AccountWithdrawalRepository;
import koready_backend.account.application.port.AccountWithdrawalRepository.WithdrawalState;
import koready_backend.user.application.exception.UserUnavailableException;

@Service
public class AccountWithdrawalService {

	private static final Duration GRACE_PERIOD = Duration.ofDays(7);
	private static final Duration MESSAGE_RETENTION = Duration.ofDays(30);
	private static final int BATCH_SIZE = 20;

	private final AccountWithdrawalRepository repository;
	private final AccountAssetDeletionPort assets;
	private final Clock clock;

	@Autowired
	public AccountWithdrawalService(
		AccountWithdrawalRepository repository,
		AccountAssetDeletionPort assets
	) {
		this(repository, assets, Clock.systemUTC());
	}

	AccountWithdrawalService(
		AccountWithdrawalRepository repository,
		AccountAssetDeletionPort assets,
		Clock clock
	) {
		this.repository = repository;
		this.assets = assets;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public WithdrawalState get(String publicId) {
		return repository.find(publicId).orElseThrow(UserUnavailableException::new);
	}

	@Transactional
	public WithdrawalState request(String publicId) {
		var now = clock.instant();
		return repository.request(publicId, now, now.plus(GRACE_PERIOD))
			.orElseThrow(UserUnavailableException::new);
	}

	@Transactional
	public WithdrawalState cancel(String publicId) {
		return repository.cancel(publicId, clock.instant())
			.orElseThrow(UserUnavailableException::new);
	}

	@Scheduled(fixedDelayString = "${koready.account-withdrawal.worker-delay:PT1H}")
	@Transactional
	public void processDueConfirmations() {
		var now = clock.instant();
		for (long userId : repository.findDueForConfirmation(now, BATCH_SIZE)) {
			List<String> keys = repository.findProfileImageKeys(userId);
			keys.forEach(assets::delete);
			repository.confirm(userId, now, now.plus(MESSAGE_RETENTION));
		}
	}

	@Scheduled(fixedDelayString = "${koready.account-withdrawal.purge-delay:PT6H}")
	@Transactional
	public void processDueMessagePurges() {
		var now = clock.instant();
		for (long userId : repository.findDueForMessagePurge(now, BATCH_SIZE)) {
			repository.purgeMessagesAndTombstone(userId);
		}
	}
}
