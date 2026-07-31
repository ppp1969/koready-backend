package koready_backend.buddy.application.port;

import java.time.Instant;
import java.util.Optional;

public interface ProfileImageRepository {

	void savePending(ImageRecord image);

	Optional<ImageRecord> findOwned(String imageId, long userId);

	Optional<ImageRecord> findViewable(String imageId, String viewerPublicId);

	void markReady(String imageId, long actualSize, Instant completedAt);

	record ImageRecord(
		String imageId,
		long userId,
		String objectKey,
		String contentType,
		long declaredSize,
		Long actualSize,
		ImageStatus status,
		Instant createdAt,
		Instant completedAt
	) {
	}

	enum ImageStatus {
		PENDING,
		READY
	}
}
