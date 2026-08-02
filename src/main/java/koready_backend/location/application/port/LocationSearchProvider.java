package koready_backend.location.application.port;

import java.util.List;
import java.util.Optional;

import koready_backend.location.domain.LocationSearchCandidate;

public interface LocationSearchProvider {

	List<LocationSearchCandidate> search(String query, int limit);

	default Optional<String> resolvePostalCode(double latitude, double longitude) {
		return Optional.empty();
	}
}
