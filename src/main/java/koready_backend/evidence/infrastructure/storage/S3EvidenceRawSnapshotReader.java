package koready_backend.evidence.infrastructure.storage;

import java.io.InputStream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import koready_backend.evidence.application.port.EvidenceRawSnapshotReader;
import koready_backend.kto.infrastructure.config.KtoS3SnapshotProperties;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

@Component
@ConditionalOnProperty(prefix = "koready.kto.snapshot", name = "storage", havingValue = "s3")
public final class S3EvidenceRawSnapshotReader implements EvidenceRawSnapshotReader {

	private final S3Client s3Client;
	private final String bucket;

	public S3EvidenceRawSnapshotReader(S3Client s3Client, KtoS3SnapshotProperties properties) {
		this.s3Client = s3Client;
		this.bucket = properties.requiredBucket();
		properties.requiredRegion();
	}

	@Override
	public InputStream open(String storageKey) {
		if (storageKey == null || storageKey.isBlank() || storageKey.length() > 1024) {
			throw new IllegalArgumentException("Evidence raw snapshot key is invalid");
		}
		try {
			return s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(storageKey).build());
		} catch (SdkException exception) {
			throw new IllegalStateException("Evidence raw snapshot is unavailable", exception);
		}
	}
}
