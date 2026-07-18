package koready_backend.onboarding.domain;

import java.util.List;

public record CandidatePlaceReadiness(
	long placeId,
	boolean ready,
	List<String> reasons
) {

	public CandidatePlaceReadiness {
		if (placeId <= 0) {
			throw new IllegalArgumentException("Place ID must be positive");
		}
		reasons = List.copyOf(reasons);
		if (ready && !reasons.isEmpty()) {
			throw new IllegalArgumentException("A ready place cannot have failure reasons");
		}
	}

	public static CandidatePlaceReadiness ready(long placeId) {
		return new CandidatePlaceReadiness(placeId, true, List.of());
	}

	public static CandidatePlaceReadiness notReady(long placeId, List<String> reasons) {
		if (reasons == null || reasons.isEmpty()) {
			throw new IllegalArgumentException("A not-ready place needs at least one reason");
		}
		return new CandidatePlaceReadiness(placeId, false, reasons);
	}
}
