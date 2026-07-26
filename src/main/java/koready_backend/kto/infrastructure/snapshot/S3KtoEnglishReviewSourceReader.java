package koready_backend.kto.infrastructure.snapshot;

import java.util.Collection;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import koready_backend.kto.application.port.KtoEnglishReviewSourceReader;
import koready_backend.kto.domain.KtoEnglishPlaceItem;
import koready_backend.kto.infrastructure.config.KtoS3SnapshotProperties;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

@Component
@ConditionalOnProperty(prefix = "koready.kto.snapshot", name = "storage", havingValue = "s3")
final class S3KtoEnglishReviewSourceReader implements KtoEnglishReviewSourceReader {

	private final S3Client s3Client;
	private final String bucket;
	private final KtoEnglishReviewSnapshotParser parser;

	S3KtoEnglishReviewSourceReader(
		S3Client s3Client,
		KtoS3SnapshotProperties properties,
		KtoEnglishReviewSnapshotParser parser
	) {
		this.s3Client = s3Client;
		this.bucket = properties.requiredBucket();
		properties.requiredRegion();
		this.parser = parser;
	}

	@Override
	public Map<String, KtoEnglishPlaceItem> findAll(
		String storageKey,
		Collection<String> sourceContentIds
	) {
		if (storageKey == null || storageKey.isBlank() || storageKey.length() > 1024
			|| !storageKey.startsWith("kto/") || storageKey.contains("..")) {
			throw new IllegalArgumentException("KTO English review snapshot key is invalid");
		}
		try {
			return parser.parse(
				s3Client.getObject(GetObjectRequest.builder()
					.bucket(bucket)
					.key(storageKey)
					.build()),
				sourceContentIds);
		} catch (SdkException exception) {
			throw new IllegalStateException("KTO English review snapshot is unavailable", exception);
		}
	}
}
