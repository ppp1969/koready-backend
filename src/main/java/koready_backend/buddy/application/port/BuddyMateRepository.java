package koready_backend.buddy.application.port;

import java.time.Instant;
import java.util.List;

import koready_backend.buddy.application.port.BuddyProfileRepository.BuddyProfileRecord;

public interface BuddyMateRepository {

	boolean existsVisiblePlace(long placeId);

	List<MateRow> findAll(MateQuery query);

	record MateQuery(
		long requesterUserId,
		long placeId,
		MateCursor cursor,
		int limit
	) {
	}

	record MateCursor(Instant savedAt, long savedPlaceId) {
	}

	record MateRow(
		long savedPlaceId,
		Instant savedAt,
		BuddyProfileRecord profile
	) {
	}
}
