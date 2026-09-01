package koready_backend.location.application.port;

import java.util.List;

import koready_backend.location.domain.LocationSearchCandidate;

public interface EnglishLocationSearchProvider {

	List<LocationSearchCandidate> search(String query, int limit);
}
