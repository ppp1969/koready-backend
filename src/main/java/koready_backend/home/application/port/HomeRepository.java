package koready_backend.home.application.port;

import java.util.Optional;

import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.ServiceRegionCode;

public interface HomeRepository {

	Optional<HomeUser> findByPublicId(String userPublicId);

	record HomeUser(
		long userId,
		String userPublicId,
		PlaceLanguage preferredLanguage,
		HomeLocation currentLocation
	) {
	}

	record HomeLocation(
		long locationId,
		String displayName,
		ServiceRegionCode serviceRegionCode
	) {
	}
}
