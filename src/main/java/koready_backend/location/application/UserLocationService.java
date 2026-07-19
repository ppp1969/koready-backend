package koready_backend.location.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.location.application.exception.UserLocationNotFoundException;
import koready_backend.location.application.exception.UserLocationUserUnavailableException;
import koready_backend.location.application.port.LocationSearchTokenCodec;
import koready_backend.location.application.port.UserLocationRepository;
import koready_backend.location.application.port.UserLocationRepository.NewLocation;
import koready_backend.location.application.port.UserLocationRepository.UserAccount;
import koready_backend.location.application.port.UserLocationRepository.UserLocationRecord;
import koready_backend.location.domain.LocationSearchCandidate;
import koready_backend.place.domain.ServiceRegionCode;

@Service
public class UserLocationService {

	private static final int MAX_TOKEN_LENGTH = 8 * 1024;
	private static final int MAX_CUSTOM_LABEL_LENGTH = 30;

	private final UserLocationRepository repository;
	private final LocationSearchTokenCodec tokenCodec;
	private final Clock clock;

	@Autowired
	public UserLocationService(
		UserLocationRepository repository,
		LocationSearchTokenCodec tokenCodec
	) {
		this(repository, tokenCodec, Clock.systemUTC());
	}

	UserLocationService(
		UserLocationRepository repository,
		LocationSearchTokenCodec tokenCodec,
		Clock clock
	) {
		this.repository = repository;
		this.tokenCodec = tokenCodec;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public LocationList getAll(String userPublicId) {
		UserAccount user = repository.findActiveUser(userPublicId)
			.orElseThrow(UserLocationUserUnavailableException::new);
		List<Location> items = repository.findAllCompleteActive(
			user.userId(), user.defaultLocationId()).stream()
			.map(record -> toLocation(
				record, Objects.equals(record.locationId(), user.defaultLocationId())))
			.toList();
		return new LocationList(items);
	}

	@Transactional
	public Location create(String userPublicId, CreateCommand command) {
		Objects.requireNonNull(command, "Location create command is required");
		UserAccount user = repository.findActiveUserForUpdate(userPublicId)
			.orElseThrow(UserLocationUserUnavailableException::new);
		var payload = tokenCodec.verify(command.searchResultToken());
		LocationSearchCandidate candidate = payload.candidate();
		Instant now = clock.instant();
		UserLocationRecord created = repository.create(
			user.userId(),
			new NewLocation(
				candidate.name(),
				command.customLabel(),
				"KAKAO",
				candidate.providerPlaceId(),
				candidate.roadAddress(),
				candidate.address(),
				candidate.latitude(),
				candidate.longitude(),
				candidate.sido(),
				candidate.sigungu(),
				candidate.dong(),
				payload.serviceRegionCode()),
			now);
		boolean makeDefault = command.setDefault() || user.defaultLocationId() == null;
		if (makeDefault) {
			repository.updateDefaultLocation(user.userId(), created.locationId(), now);
		}
		return toLocation(created, makeDefault);
	}

	@Transactional
	public Location setDefault(String userPublicId, long locationId) {
		positive(locationId);
		UserAccount user = repository.findActiveUserForUpdate(userPublicId)
			.orElseThrow(UserLocationUserUnavailableException::new);
		UserLocationRecord location = repository
			.findCompleteActive(user.userId(), locationId)
			.orElseThrow(() -> new UserLocationNotFoundException(locationId));
		repository.updateDefaultLocation(user.userId(), locationId, clock.instant());
		return toLocation(location, true);
	}

	@Transactional
	public void delete(String userPublicId, long locationId) {
		positive(locationId);
		UserAccount user = repository.findActiveUserForUpdate(userPublicId)
			.orElseThrow(UserLocationUserUnavailableException::new);
		repository.findCompleteActive(user.userId(), locationId)
			.orElseThrow(() -> new UserLocationNotFoundException(locationId));
		Instant now = clock.instant();
		if (Objects.equals(user.defaultLocationId(), locationId)) {
			Long replacementId = repository
				.findNewestCompleteActiveExcluding(user.userId(), locationId)
				.map(UserLocationRecord::locationId)
				.orElse(null);
			repository.updateDefaultLocation(user.userId(), replacementId, now);
		}
		repository.softDelete(user.userId(), locationId, now);
	}

	private static Location toLocation(UserLocationRecord record, boolean isDefault) {
		return new Location(
			record.locationId(),
			record.displayName(),
			record.customLabel(),
			record.roadAddress(),
			record.address(),
			record.latitude(),
			record.longitude(),
			record.serviceRegionCode(),
			isDefault,
			record.createdAt());
	}

	private static void positive(long locationId) {
		if (locationId <= 0) {
			throw new IllegalArgumentException("Location ID must be positive");
		}
	}

	public record CreateCommand(
		String searchResultToken,
		String customLabel,
		boolean setDefault
	) {
		public CreateCommand {
			searchResultToken = normalizeRequired(
				searchResultToken, MAX_TOKEN_LENGTH, "Location search token");
			customLabel = normalizeNullable(
				customLabel, MAX_CUSTOM_LABEL_LENGTH, "Location custom label");
		}
	}

	public record LocationList(List<Location> items) {
		public LocationList {
			items = List.copyOf(items);
		}
	}

	public record Location(
		long locationId,
		String displayName,
		String customLabel,
		String roadAddress,
		String address,
		double latitude,
		double longitude,
		ServiceRegionCode serviceRegionCode,
		boolean isDefault,
		Instant createdAt
	) {
	}

	private static String normalizeRequired(String value, int maxLength, String name) {
		String normalized = normalizeNullable(value, maxLength, name);
		if (normalized == null) {
			throw new IllegalArgumentException(name + " is required");
		}
		return normalized;
	}

	private static String normalizeNullable(String value, int maxLength, String name) {
		if (value == null) {
			return null;
		}
		String normalized = value.strip();
		if (normalized.length() > maxLength) {
			throw new IllegalArgumentException(name + " is too long");
		}
		return normalized.isEmpty() ? null : normalized;
	}
}
