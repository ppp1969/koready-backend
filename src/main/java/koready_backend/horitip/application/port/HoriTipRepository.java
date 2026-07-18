package koready_backend.horitip.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import koready_backend.horitip.domain.HoriTipDraft;
import koready_backend.horitip.domain.HoriTipStatus;

public interface HoriTipRepository {

	Optional<HoriTipRecord> insertDraft(NewHoriTip tip);

	Optional<HoriTipRecord> findById(long id);

	Optional<HoriTipRecord> findByIdForUpdate(long id);

	List<HoriTipRecord> findPage(ListCriteria criteria);

	Set<Long> findVisiblePlaceIds(List<Long> placeIds);

	HoriTipRecord updateDraft(long id, HoriTipDraft draft, String actorSubject, Instant now);

	HoriTipRecord updateStatus(long id, HoriTipStatus status, String actorSubject, Instant now);

	void recordAudit(AuditRecord audit);

	record NewHoriTip(
		String code,
		HoriTipDraft draft,
		String actorSubject,
		Instant now
	) {
	}

	record ListCriteria(
		HoriTipStatus status,
		String code,
		Long destinationPlaceId,
		Long beforeId,
		int limit
	) {
	}

	record HoriTipRecord(
		long id,
		String code,
		HoriTipStatus status,
		HoriTipDraft draft,
		int version,
		String createdBySubject,
		String updatedBySubject,
		Instant activatedAt,
		Instant archivedAt,
		Instant createdAt,
		Instant updatedAt
	) {
	}

	record AuditRecord(
		String action,
		String actorSubject,
		String reason,
		HoriTipRecord before,
		HoriTipRecord after,
		Instant createdAt
	) {
	}
}
