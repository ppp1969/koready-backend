package koready_backend.evidence.infrastructure.storage;

import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import koready_backend.evidence.application.port.EvidenceBundleArtifactStore;
import koready_backend.evidence.infrastructure.config.EvidenceBundleProperties;
import koready_backend.kto.infrastructure.config.KtoS3SnapshotProperties;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
@ConditionalOnProperty(prefix = "koready.kto.snapshot", name = "storage", havingValue = "s3")
public final class S3EvidenceBundleArtifactStore implements EvidenceBundleArtifactStore {

	private static final Pattern BUNDLE_ID = Pattern.compile("evidence_[A-Za-z0-9]{8,64}");
	private static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9._-]{1,200}");

	private final S3Client s3Client;
	private final S3Presigner presigner;
	private final String bucket;
	private final Duration expiration;

	public S3EvidenceBundleArtifactStore(
		S3Client s3Client,
		S3Presigner presigner,
		KtoS3SnapshotProperties s3Properties,
		EvidenceBundleProperties properties
	) {
		this.s3Client = s3Client;
		this.presigner = presigner;
		this.bucket = s3Properties.requiredBucket();
		s3Properties.requiredRegion();
		this.expiration = properties.requiredDownloadExpiration();
	}

	@Override
	public StoredArtifact store(String bundleId, String fileName, Path source, String sha256, long byteSize) {
		if (!BUNDLE_ID.matcher(bundleId).matches() || !FILE_NAME.matcher(fileName).matches()
			|| source == null || sha256 == null || !sha256.matches("[a-f0-9]{64}") || byteSize < 0) {
			throw new IllegalArgumentException("Evidence bundle artifact is invalid");
		}
		String storageKey = "evidence-bundles/" + bundleId + "/" + fileName;
		try {
			s3Client.putObject(PutObjectRequest.builder()
				.bucket(bucket).key(storageKey).contentType("application/zip").contentLength(byteSize)
				.metadata(java.util.Map.of("sha256", sha256)).build(), RequestBody.fromFile(source));
			return new StoredArtifact(storageKey);
		} catch (SdkException exception) {
			throw new IllegalStateException("Evidence bundle artifact could not be stored", exception);
		}
	}

	@Override
	public DownloadLink createDownloadUrl(String storageKey, String fileName) {
		if (storageKey == null || storageKey.isBlank() || !FILE_NAME.matcher(fileName).matches()) {
			throw new IllegalArgumentException("Evidence bundle download is invalid");
		}
		try {
			var request = presigner.presignGetObject(GetObjectPresignRequest.builder()
				.signatureDuration(expiration)
				.getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(storageKey)
					.responseContentType("application/zip")
					.responseContentDisposition("attachment; filename=\\\"" + fileName + "\\\"").build())
				.build());
			return new DownloadLink(request.url().toExternalForm(), request.expiration());
		} catch (SdkException | IllegalArgumentException exception) {
			throw new IllegalStateException("Evidence bundle download is unavailable", exception);
		}
	}
}
