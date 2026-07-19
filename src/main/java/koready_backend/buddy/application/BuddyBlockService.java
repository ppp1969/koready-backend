package koready_backend.buddy.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.buddy.application.exception.BuddyProfileNotFoundException;
import koready_backend.buddy.application.exception.BuddyUserUnavailableException;
import koready_backend.buddy.application.port.BuddyBlockRepository;

@Service
public class BuddyBlockService {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	private final BuddyBlockRepository repository;
	private final Clock clock;

	@Autowired
	public BuddyBlockService(BuddyBlockRepository repository) {
		this(repository, Clock.system(SEOUL_ZONE));
	}

	BuddyBlockService(BuddyBlockRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional
	public BlockResult block(String userPublicId, long profileId) {
		UserPair users = resolveUsers(userPublicId, profileId);
		Instant blockedAt = repository.block(
			users.blockerUserId(), users.blockedUserId(), clock.instant());
		return new BlockResult(profileId, blockedAt);
	}

	@Transactional
	public void unblock(String userPublicId, long profileId) {
		UserPair users = resolveUsers(userPublicId, profileId);
		repository.unblock(users.blockerUserId(), users.blockedUserId());
	}

	private UserPair resolveUsers(String userPublicId, long profileId) {
		if (profileId <= 0) {
			throw new IllegalArgumentException("Buddy profile ID must be positive");
		}
		long blockerUserId = repository.findActiveUserId(userPublicId)
			.orElseThrow(BuddyUserUnavailableException::new);
		long blockedUserId = repository.findActiveProfileOwnerId(profileId)
			.orElseThrow(() -> new BuddyProfileNotFoundException(profileId));
		if (blockerUserId == blockedUserId) {
			throw new IllegalArgumentException("A user cannot block their own profile");
		}
		return new UserPair(blockerUserId, blockedUserId);
	}

	public record BlockResult(
		long profileId,
		Instant blockedAt
	) {
	}

	private record UserPair(
		long blockerUserId,
		long blockedUserId
	) {
	}
}
