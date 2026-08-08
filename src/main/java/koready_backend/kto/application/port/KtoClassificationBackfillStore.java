package koready_backend.kto.application.port;

import java.util.List;

import koready_backend.kto.application.model.KtoClassificationDecision;

public interface KtoClassificationBackfillStore {

	long loadCheckpoint(String ruleVersion);

	void resetCheckpoint(String ruleVersion);

	void recordFailure(String ruleVersion);

	void applyPage(
		String ruleVersion,
		List<KtoClassificationDecision> decisions,
		long lastPlaceId
	);
}
