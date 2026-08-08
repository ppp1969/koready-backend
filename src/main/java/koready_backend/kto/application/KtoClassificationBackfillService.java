package koready_backend.kto.application;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import koready_backend.kto.application.model.KtoClassificationApplyResult;
import koready_backend.kto.application.model.KtoClassificationCandidate;
import koready_backend.kto.application.model.KtoClassificationDecision;
import koready_backend.kto.application.port.KtoClassificationBackfillStore;
import koready_backend.kto.application.port.KtoClassificationCandidateSource;
import koready_backend.kto.domain.KtoPlaceClassificationInput;
import koready_backend.kto.domain.KtoPlaceStyleRuleV1;
import koready_backend.place.domain.TravelStyle;

@Service
public class KtoClassificationBackfillService {

	private final KtoClassificationCandidateSource source;
	private final KtoClassificationBackfillStore store;
	private final KtoPlaceStyleRuleV1 rule = new KtoPlaceStyleRuleV1();

	public KtoClassificationBackfillService(
		KtoClassificationCandidateSource source,
		KtoClassificationBackfillStore store
	) {
		this.source = source;
		this.store = store;
	}

	public KtoClassificationApplyResult run(int pageSize, boolean reset) {
		if (pageSize < 1 || pageSize > 1_000) {
			throw new IllegalArgumentException("Page size must be between 1 and 1000");
		}
		String ruleVersion = KtoPlaceStyleRuleV1.VERSION;
		if (reset) {
			store.resetCheckpoint(ruleVersion);
		}
		try {
			return runFromCheckpoint(pageSize, ruleVersion);
		} catch (RuntimeException exception) {
			try {
				store.recordFailure(ruleVersion);
			} catch (RuntimeException recordingFailure) {
				exception.addSuppressed(recordingFailure);
			}
			throw exception;
		}
	}

	private KtoClassificationApplyResult runFromCheckpoint(
		int pageSize,
		String ruleVersion
	) {
		long cursor = store.loadCheckpoint(ruleVersion);
		long processed = 0;
		long classified = 0;
		long mappings = 0;

		while (true) {
			List<KtoClassificationCandidate> candidates = source.findAfter(cursor, pageSize);
			if (candidates.isEmpty()) {
				return new KtoClassificationApplyResult(
					ruleVersion,
					processed,
					classified,
					processed - classified,
					mappings,
					cursor,
					true);
			}

			long pageCursor = cursor;
			List<KtoClassificationDecision> decisions = candidates.stream()
				.map(candidate -> decision(candidate, pageCursor))
				.toList();
			for (int index = 0; index < candidates.size(); index++) {
				KtoClassificationCandidate candidate = candidates.get(index);
				if (candidate.placeId() <= cursor
					|| (index > 0 && candidate.placeId() <= candidates.get(index - 1).placeId())) {
					throw new IllegalStateException(
						"Classification candidates must advance by ascending place id");
				}
				Set<TravelStyle> styles = decisions.get(index).styles();
				processed++;
				if (!styles.isEmpty()) {
					classified++;
					mappings += styles.size();
				}
			}
			cursor = candidates.getLast().placeId();
			store.applyPage(ruleVersion, decisions, cursor);
		}
	}

	private KtoClassificationDecision decision(
		KtoClassificationCandidate candidate,
		long cursor
	) {
		if (candidate.placeId() <= cursor) {
			throw new IllegalStateException(
				"Classification candidates must advance by ascending place id");
		}
		Set<TravelStyle> styles = rule.classify(new KtoPlaceClassificationInput(
			candidate.contentTypeId(),
			candidate.classificationCode1(),
			candidate.classificationCode2(),
			candidate.classificationCode3()));
		return new KtoClassificationDecision(
			candidate.placeId(),
			candidate.contentTypeId(),
			candidate.classificationCode1(),
			candidate.classificationCode2(),
			candidate.classificationCode3(),
			styles);
	}
}
