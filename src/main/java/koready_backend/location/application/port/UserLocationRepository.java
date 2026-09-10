package koready_backend.location.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.PlaceLanguage;

public interface UserLocationRepository {

	Optional<UserAccount> findActiveUser(String publicId);

	Optional<UserAccount> findActiveUserForUpdate(String publicId);

	List<UserLocationRecord> findAllCompleteActive(
		long userId,
		Long defaultLocationId,
		PlaceLanguage language
	);

	default List<UserLocationRecord> findAllCompleteActive(long userId, Long defaultLocationId) {
		return findAllCompleteActive(userId, defaultLocationId, PlaceLanguage.KO);
	}

	Optional<UserLocationRecord> findCompleteActive(
		long userId, long locationId, PlaceLanguage language);

	default Optional<UserLocationRecord> findCompleteActive(long userId, long locationId) {
		return findCompleteActive(userId, locationId, PlaceLanguage.KO);
	}

	Optional<UserLocationRecord> findNewestCompleteActiveExcluding(
		long userId,
		long excludedLocationId
	);

	UserLocationRecord create(long userId, NewLocation location, Instant createdAt);

	void saveLocalization(
		long locationId,
		PlaceLanguage language,
		LocalizedLocation location,
		Instant updatedAt
	);

	boolean hasLocalization(long locationId, PlaceLanguage language);

	void updateDefaultLocation(long userId, Long locationId, Instant updatedAt);

	void softDelete(long userId, long locationId, Instant deletedAt);

	record UserAccount(
		long userId,
		Long defaultLocationId,
		PlaceLanguage preferredLanguage
	) {
		public UserAccount(long userId, Long defaultLocationId) {
			this(userId, defaultLocationId, PlaceLanguage.KO);
		}
	}

	record LocalizedLocation(String displayName, String roadAddress, String address) {
	}

	record NewLocation(
		String displayName,
		String customLabel,
		String provider,
		String providerPlaceId,
		String roadAddress,
		String address,
		String postalCode,
		double latitude,
		double longitude,
		String sido,
		String sigungu,
		String dong,
		ServiceRegionCode serviceRegionCode
	) {
	}

	record UserLocationRecord(
		long locationId,
		long userId,
		String displayName,
		String customLabel,
		String provider,
		String providerPlaceId,
		String roadAddress,
		String address,
		String postalCode,
		double latitude,
		double longitude,
		String sido,
		String sigungu,
		String dong,
		ServiceRegionCode serviceRegionCode,
		Instant createdAt
	) {
	}
}
