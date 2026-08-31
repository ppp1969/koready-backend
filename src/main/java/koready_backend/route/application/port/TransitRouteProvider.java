package koready_backend.route.application.port;

import java.time.ZonedDateTime;
import java.util.List;

import koready_backend.route.domain.RouteCandidate;

public interface TransitRouteProvider {

	List<RouteCandidate> findRoutes(RouteProviderRequest request);

	record RouteProviderRequest(
		double originLatitude,
		double originLongitude,
		double destinationLatitude,
		double destinationLongitude,
		ZonedDateTime departureAt
	) {
	}

}
