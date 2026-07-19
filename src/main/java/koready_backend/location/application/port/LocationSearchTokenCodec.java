package koready_backend.location.application.port;

import java.time.Instant;
import java.util.Objects;

import koready_backend.location.domain.LocationSearchCandidate;
import koready_backend.place.domain.ServiceRegionCode;

public interface LocationSearchTokenCodec {

	String issue(LocationSearchCandidate candidate, ServiceRegionCode serviceRegionCode);

	TokenPayload verify(String token);

	record TokenPayload(
		LocationSearchCandidate candidate,
		ServiceRegionCode serviceRegionCode,
		Instant expiresAt
	) {

		public TokenPayload {
			Objects.requireNonNull(candidate, "Location token candidate is required");
			Objects.requireNonNull(serviceRegionCode, "Location token region is required");
			Objects.requireNonNull(expiresAt, "Location token expiry is required");
		}
	}
}
