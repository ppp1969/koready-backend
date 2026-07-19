package koready_backend.location.application.port;

import java.util.List;

import koready_backend.location.domain.LocationSearchCandidate;

public interface LocationSearchProvider {

	List<LocationSearchCandidate> search(String query, int limit);
}
