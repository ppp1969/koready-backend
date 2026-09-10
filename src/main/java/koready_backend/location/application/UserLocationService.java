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
import koready_backend.location.application.port.LocationSearchProvider;
import koready_backend.location.application.port.EnglishLocationSearchProvider;
import koready_backend.location.application.port.UserLocationRepository;
import koready_backend.location.application.port.UserLocationRepository.LocalizedLocation;
import koready_backend.location.application.port.UserLocationRepository.NewLocation;
import koready_backend.location.application.port.UserLocationRepository.UserAccount;
import koready_backend.location.application.port.UserLocationRepository.UserLocationRecord;
import koready_backend.location.domain.LocationSearchCandidate;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.PlaceLanguage;

@Service
public class UserLocationService {

	private static final int MAX_TOKEN_LENGTH = 8 * 1024;
	private static final int MAX_CUSTOM_LABEL_LENGTH = 30;

	private final UserLocationRepository repository;
	private final LocationSearchTokenCodec tokenCodec;
	private final LocationSearchProvider searchProvider;
	private final EnglishLocationSearchProvider englishSearchProvider;
	private final Clock clock;

	@Autowired
	public UserLocationService(
		UserLocationRepository repository,
		LocationSearchTokenCodec tokenCodec,
		LocationSearchProvider searchProvider,
		EnglishLocationSearchProvider englishSearchProvider
	) {
		this(repository, tokenCodec, searchProvider, englishSearchProvider, Clock.systemUTC());
	}

	UserLocationService(
		UserLocationRepository repository,
		LocationSearchTokenCodec tokenCodec,
		Clock clock
	) {
		this(repository, tokenCodec, (query, limit) -> List.of(),
			(query, limit) -> List.of(), clock);
	}

	UserLocationService(
		UserLocationRepository repository,
		LocationSearchTokenCodec tokenCodec,
		LocationSearchProvider searchProvider,
		Clock clock
	) {
		this(repository, tokenCodec, searchProvider, (query, limit) -> List.of(), clock);
	}

	UserLocationService(
		UserLocationRepository repository,
		LocationSearchTokenCodec tokenCodec,
		LocationSearchProvider searchProvider,
		EnglishLocationSearchProvider englishSearchProvider,
		Clock clock
	) {
		this.repository = repository;
		this.tokenCodec = tokenCodec;
		this.searchProvider = searchProvider;
		this.englishSearchProvider = englishSearchProvider;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public LocationList getAll(String userPublicId) {
		UserAccount user = repository.findActiveUser(userPublicId)
			.orElseThrow(UserLocationUserUnavailableException::new);
		List<Location> items = repository.findAllCompleteActive(
			user.userId(), user.defaultLocationId(), user.preferredLanguage()).stream()
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
		String postalCode = candidate.postalCode();
		if (postalCode == null) {
			postalCode = searchProvider.resolvePostalCode(
				candidate.latitude(), candidate.longitude()).orElse(null);
		}
		Instant now = clock.instant();
		UserLocationRecord created = repository.create(
			user.userId(),
			new NewLocation(
				candidate.name(),
				command.customLabel(),
				candidate.provider(),
				candidate.providerPlaceId(),
				candidate.roadAddress(),
				candidate.address(),
				postalCode,
				candidate.latitude(),
				candidate.longitude(),
				candidate.sido(),
				candidate.sigungu(),
				candidate.dong(),
				payload.serviceRegionCode()),
			now);
		repository.saveLocalization(
			created.locationId(), candidate.language(), localized(candidate), now);
		findCounterpart(candidate).ifPresent(counterpart -> repository.saveLocalization(
			created.locationId(), counterpart.language(), localized(counterpart), now));
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
			.findCompleteActive(user.userId(), locationId, user.preferredLanguage())
			.orElseThrow(() -> new UserLocationNotFoundException(locationId));
		repository.updateDefaultLocation(user.userId(), locationId, clock.instant());
		return toLocation(location, true);
	}

	@Transactional
	public void delete(String userPublicId, long locationId) {
		positive(locationId);
		UserAccount user = repository.findActiveUserForUpdate(userPublicId)
			.orElseThrow(UserLocationUserUnavailableException::new);
		repository.findCompleteActive(user.userId(), locationId, user.preferredLanguage())
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

	@Transactional
	public void prepareLanguage(String userPublicId, PlaceLanguage language) {
		try {
			java.util.Optional<UserAccount> account = repository.findActiveUser(userPublicId);
			if (account.isEmpty()) {
				return;
			}
			UserAccount user = account.get();
			List<UserLocationRecord> locations = repository.findAllCompleteActive(
				user.userId(), user.defaultLocationId(), language);
			for (UserLocationRecord location : locations) {
				if (repository.hasLocalization(location.locationId(), language)) {
					continue;
				}
				findLocalized(location, language).ifPresent(candidate ->
					repository.saveLocalization(location.locationId(), language,
						localized(candidate), clock.instant()));
			}
		} catch (RuntimeException ignored) {
			// Language preference must remain usable when localization providers fail.
		}
	}

	private java.util.Optional<LocationSearchCandidate> findCounterpart(
		LocationSearchCandidate source
	) {
		try {
			if (source.language() == PlaceLanguage.EN) {
				return searchProvider.resolveByCoordinates(
					source.latitude(), source.longitude());
			}
			return englishSearchProvider.search(source.name(), 10).stream()
				.min(java.util.Comparator.comparingDouble(candidate ->
					distanceSquared(source, candidate)))
				.filter(candidate -> distanceSquared(source, candidate) < 0.01);
		} catch (RuntimeException ignored) {
			return java.util.Optional.empty();
		}
	}

	private java.util.Optional<LocationSearchCandidate> findLocalized(
		UserLocationRecord source,
		PlaceLanguage target
	) {
		try {
			if (target == PlaceLanguage.KO) {
				return searchProvider.resolveByCoordinates(
					source.latitude(), source.longitude());
			}
			LocationSearchCandidate anchor = new LocationSearchCandidate(
				source.provider(), PlaceLanguage.KO,
				koready_backend.location.domain.LocationSearchResultType.PLACE,
				source.providerPlaceId(), source.displayName(), source.roadAddress(),
				source.address(), source.latitude(), source.longitude(), source.sido(),
				source.sigungu(), source.dong(), source.postalCode());
			return englishSearchProvider.search(source.displayName(), 10).stream()
				.min(java.util.Comparator.comparingDouble(candidate ->
					distanceSquared(anchor, candidate)))
				.filter(candidate -> distanceSquared(anchor, candidate) < 0.01);
		} catch (RuntimeException ignored) {
			return java.util.Optional.empty();
		}
	}

	private static double distanceSquared(
		LocationSearchCandidate left,
		LocationSearchCandidate right
	) {
		double latitude = left.latitude() - right.latitude();
		double longitude = left.longitude() - right.longitude();
		return latitude * latitude + longitude * longitude;
	}

	private static LocalizedLocation localized(LocationSearchCandidate candidate) {
		return new LocalizedLocation(
			candidate.name(), candidate.roadAddress(), candidate.address());
	}

	private static Location toLocation(UserLocationRecord record, boolean isDefault) {
		return new Location(
			record.locationId(),
			record.displayName(),
			record.customLabel(),
			record.roadAddress(),
			record.address(),
			record.postalCode(),
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
		String postalCode,
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
