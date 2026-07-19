package koready_backend.location.infrastructure.provider;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import koready_backend.location.application.exception.LocationProviderUnavailableException;
import koready_backend.location.application.port.LocationSearchProvider;
import koready_backend.location.domain.LocationSearchCandidate;

@Component
@ConditionalOnProperty(
	name = "koready.location.search.provider",
	havingValue = "disabled",
	matchIfMissing = true)
public final class UnavailableLocationSearchProvider implements LocationSearchProvider {

	@Override
	public List<LocationSearchCandidate> search(String query, int limit) {
		throw new LocationProviderUnavailableException();
	}
}
