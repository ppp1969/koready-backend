package koready_backend.onboarding.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.onboarding.application.exception.CandidateSetConcurrentModificationException;
import koready_backend.onboarding.application.port.CandidateSetRepository;
import koready_backend.onboarding.application.port.CandidateSetRepository.AuditRecord;
import koready_backend.onboarding.application.port.CandidateSetRepository.CandidateItemRecord;
import koready_backend.onboarding.application.port.CandidateSetRepository.CandidateSetRecord;
import koready_backend.onboarding.domain.CandidatePlaceReadiness;
import koready_backend.onboarding.domain.CandidateSetDraft;
import koready_backend.onboarding.domain.CandidateSetItemDraft;
import koready_backend.onboarding.domain.CandidateSetStatus;
import koready_backend.onboarding.domain.InitialCandidatePlace;
import koready_backend.onboarding.domain.InitialCandidatePlaceCatalog;

@Service
public class InitialCandidateSetBootstrapService {

	public static final String CANDIDATE_SET_ID = "onb-kto-curated-v1";
	private static final String TITLE = "KoReady 온보딩 대표 관광지 v1";
	private static final String ACTOR = "SYSTEM:CURATED_KTO_BOOTSTRAP";

	private final CandidateSetRepository repository;
	private final Clock clock;

	@Autowired
	public InitialCandidateSetBootstrapService(CandidateSetRepository repository) {
		this(repository, Clock.systemUTC());
	}

	InitialCandidateSetBootstrapService(CandidateSetRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional
	public InitialCandidateSetBootstrapResult bootstrap(Map<String, Long> placeIdsByContentId) {
		CandidateSetDraft desired = desiredDraft(placeIdsByContentId);
		Instant now = clock.instant();
		CandidateSetRecord existing = repository.findByPublicIdForUpdate(CANDIDATE_SET_ID)
			.orElse(null);

		if (existing == null) {
			existing = repository.insertDraft(CANDIDATE_SET_ID, TITLE, now);
			repository.recordAudit(new AuditRecord(
				ACTOR,
				"CURATION_SET_CREATED",
				CANDIDATE_SET_ID,
				null,
				CandidateSetStatus.DRAFT,
				now));
		}

		if (existing.status() == CandidateSetStatus.PUBLISHED) {
			requireIdenticalPublishedSet(existing, desired);
			repository.replaceCurrent(existing.id(), now);
			repository.recordAudit(new AuditRecord(
				ACTOR,
				"CURATION_SET_BOOTSTRAP_REPLAYED",
				CANDIDATE_SET_ID,
				CandidateSetStatus.PUBLISHED,
				CandidateSetStatus.PUBLISHED,
				now));
			return new InitialCandidateSetBootstrapResult(CANDIDATE_SET_ID, true);
		}

		if (existing.status() != CandidateSetStatus.DRAFT) {
			throw new IllegalStateException("The deterministic initial candidate set is archived");
		}

		repository.replaceDraft(existing.id(), desired, now);
		Map<Long, CandidatePlaceReadiness> readiness = repository.findPlaceReadiness(desired.items());
		desired.requirePublishable(readiness);
		if (!repository.markPublished(existing.id(), ACTOR, now)) {
			throw new CandidateSetConcurrentModificationException();
		}
		repository.replaceCurrent(existing.id(), now);
		repository.recordAudit(new AuditRecord(
			ACTOR,
			"CURATION_SET_PUBLISHED",
			CANDIDATE_SET_ID,
			CandidateSetStatus.DRAFT,
			CandidateSetStatus.PUBLISHED,
			now));
		return new InitialCandidateSetBootstrapResult(CANDIDATE_SET_ID, false);
	}

	private CandidateSetDraft desiredDraft(Map<String, Long> placeIdsByContentId) {
		if (placeIdsByContentId == null) {
			throw new IllegalArgumentException("Imported KTO place IDs are required");
		}
		List<CandidateSetItemDraft> items = InitialCandidatePlaceCatalog.approved().stream()
			.map(place -> item(place, requirePlaceId(placeIdsByContentId, place)))
			.toList();
		return new CandidateSetDraft(TITLE, items);
	}

	private long requirePlaceId(
		Map<String, Long> placeIdsByContentId,
		InitialCandidatePlace place
	) {
		Long placeId = placeIdsByContentId.get(place.ktoContentId());
		if (placeId == null || placeId <= 0) {
			throw new IllegalStateException(
				"Approved KTO place ID is missing: " + place.ktoContentId());
		}
		return placeId;
	}

	private CandidateSetItemDraft item(InitialCandidatePlace place, long placeId) {
		return new CandidateSetItemDraft(
			placeId,
			place.displayOrder(),
			null,
			place.curatorMessageKo(),
			place.curatorMessageEn(),
			place.displayTags(),
			place.editorNote());
	}

	private void requireIdenticalPublishedSet(
		CandidateSetRecord existing,
		CandidateSetDraft desired
	) {
		List<CandidateSetItemDraft> actualItems = repository.findItems(existing.id()).stream()
			.map(CandidateItemRecord::toDraft)
			.toList();
		if (!TITLE.equals(existing.title()) || !desired.items().equals(actualItems)) {
			throw new IllegalStateException(
				"Published initial candidate set differs from the approved catalog");
		}
	}
}
