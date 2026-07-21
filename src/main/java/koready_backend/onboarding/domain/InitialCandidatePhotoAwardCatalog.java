package koready_backend.onboarding.domain;

import java.util.List;
import java.util.Map;

/** Operator-reviewed links only. Photo-award IDs are not TourAPI place IDs. */
public final class InitialCandidatePhotoAwardCatalog {

	private static final Map<String, List<String>> APPROVED = Map.of();

	private InitialCandidatePhotoAwardCatalog() {
	}

	public static Map<String, List<String>> approved() {
		return APPROVED;
	}
}
