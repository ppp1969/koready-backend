package koready_backend.route.application.port;

import java.time.Instant;
import java.util.Optional;

import koready_backend.route.domain.RoutePlan;

public interface RouteRepository {

	Optional<RouteContext> findContext(String userSubject, long locationId, long placeId);

	void save(long userId, RoutePlan route);

	Optional<RoutePlan> findOwned(String routeId, String userSubject);

	record RouteContext(
		long userId,
		String language,
		String originName,
		String originAddress,
		double originLatitude,
		double originLongitude,
		String destinationName,
		String destinationAddress,
		double destinationLatitude,
		double destinationLongitude
	) {
	}

	default void deleteExpired(Instant now) {
	}
}
