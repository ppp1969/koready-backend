package koready_backend.location.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import koready_backend.place.domain.ServiceRegionCode;

public interface UserLocationRepository {

	Optional<UserAccount> findActiveUser(String publicId);

	Optional<UserAccount> findActiveUserForUpdate(String publicId);

	List<UserLocationRecord> findAllCompleteActive(
		long userId,
		Long defaultLocationId
	);

	Optional<UserLocationRecord> findCompleteActive(long userId, long locationId);

	Optional<UserLocationRecord> findNewestCompleteActiveExcluding(
		long userId,
		long excludedLocationId
	);

	UserLocationRecord create(long userId, NewLocation location, Instant createdAt);

	void updateDefaultLocation(long userId, Long locationId, Instant updatedAt);

	void softDelete(long userId, long locationId, Instant deletedAt);

	record UserAccount(long userId, Long defaultLocationId) {
	}

	record NewLocation(
		String displayName,
		String customLabel,
		String provider,
		String providerPlaceId,
		String roadAddress,
		String address,
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
