package koready_backend.kto.infrastructure.snapshot;

import java.time.Duration;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import koready_backend.kto.application.exception.KtoSnapshotDownloadException;
import koready_backend.kto.application.port.KtoRawSnapshotDownloadUrlProvider;
import koready_backend.kto.infrastructure.config.KtoS3SnapshotProperties;
import koready_backend.kto.infrastructure.config.KtoSnapshotDownloadProperties;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
@ConditionalOnProperty(
	prefix = "koready.kto.snapshot",
	name = "storage",
	havingValue = "s3")
public final class S3KtoRawSnapshotDownloadUrlProvider
	implements KtoRawSnapshotDownloadUrlProvider {

	private static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9._-]{1,180}");

	private final S3Presigner presigner;
	private final String bucket;
	private final Duration expiration;

	public S3KtoRawSnapshotDownloadUrlProvider(
		S3Presigner presigner,
		KtoS3SnapshotProperties s3Properties,
		KtoSnapshotDownloadProperties properties
	) {
		this.presigner = presigner;
		this.bucket = s3Properties.requiredBucket();
		s3Properties.requiredRegion();
		this.expiration = properties.requiredExpiration();
	}

	@Override
	public boolean available() {
		return true;
	}

	@Override
	public DownloadLink issue(String storageKey, String fileName) {
		if (storageKey == null || storageKey.isBlank() || storageKey.length() > 1024
			|| fileName == null || !FILE_NAME.matcher(fileName).matches()) {
			throw new KtoSnapshotDownloadException();
		}
		try {
			GetObjectRequest getObjectRequest = GetObjectRequest.builder()
				.bucket(bucket)
				.key(storageKey)
				.responseContentType("application/gzip")
				.responseContentDisposition(
					"attachment; filename=\"" + fileName + "\"")
				.build();
			var request = presigner.presignGetObject(GetObjectPresignRequest.builder()
				.signatureDuration(expiration)
				.getObjectRequest(getObjectRequest)
				.build());
			return new DownloadLink(request.url().toExternalForm(), request.expiration());
		} catch (SdkException | IllegalArgumentException exception) {
			throw new KtoSnapshotDownloadException();
		}
	}
}
