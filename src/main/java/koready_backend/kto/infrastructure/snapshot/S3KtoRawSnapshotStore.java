package koready_backend.kto.infrastructure.snapshot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import koready_backend.kto.application.exception.KtoSnapshotConflictException;
import koready_backend.kto.application.exception.KtoSnapshotStorageException;
import koready_backend.kto.application.model.KtoRawSnapshot;
import koready_backend.kto.application.model.KtoStoredSnapshotMetadata;
import koready_backend.kto.application.port.KtoRawSnapshotStore;
import koready_backend.kto.infrastructure.config.KtoS3SnapshotProperties;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
@ConditionalOnProperty(
	prefix = "koready.kto.snapshot",
	name = "storage",
	havingValue = "s3")
public final class S3KtoRawSnapshotStore implements KtoRawSnapshotStore {

	private static final int MAX_CONDITIONAL_WRITE_ATTEMPTS = 3;

	private final S3Client s3Client;
	private final String bucket;

	public S3KtoRawSnapshotStore(
		S3Client s3Client,
		KtoS3SnapshotProperties properties
	) {
		this.s3Client = s3Client;
		this.bucket = properties.requiredBucket();
		properties.requiredRegion();
	}

	@Override
	public KtoStoredSnapshotMetadata store(KtoRawSnapshot snapshot) {
		String storageKey = KtoSnapshotObjectKeyFactory.create(snapshot);
		byte[] compressed = gzip(snapshot.payload());
		String storedHash = sha256(compressed);
		PutObjectRequest request = PutObjectRequest.builder()
			.bucket(bucket)
			.key(storageKey)
			.ifNoneMatch("*")
			.contentType("application/gzip")
			.contentLength((long) compressed.length)
			.metadata(Map.of(
				"raw-sha256", snapshot.rawContentSha256(),
				"stored-sha256", storedHash,
				"captured-at", snapshot.capturedAt().toString()))
			.build();

		for (int attempt = 1; attempt <= MAX_CONDITIONAL_WRITE_ATTEMPTS; attempt++) {
			try {
				s3Client.putObject(request, RequestBody.fromBytes(compressed));
				return metadata(snapshot, storageKey, storedHash, compressed.length);
			} catch (S3Exception exception) {
				if (exception.statusCode() == 412) {
					return replay(snapshot, storageKey);
				}
				if (exception.statusCode() != 409
					|| attempt == MAX_CONDITIONAL_WRITE_ATTEMPTS) {
					throw new KtoSnapshotStorageException();
				}
			} catch (SdkException exception) {
				throw new KtoSnapshotStorageException();
			}
		}
		throw new KtoSnapshotStorageException();
	}

	private KtoStoredSnapshotMetadata replay(
		KtoRawSnapshot snapshot,
		String storageKey
	) {
		try {
			byte[] existing = s3Client.getObjectAsBytes(GetObjectRequest.builder()
				.bucket(bucket)
				.key(storageKey)
				.build()).asByteArray();
			if (!snapshot.rawContentSha256().equals(uncompressedSha256(existing))) {
				throw new KtoSnapshotConflictException();
			}
			return metadata(snapshot, storageKey, sha256(existing), existing.length);
		} catch (KtoSnapshotConflictException exception) {
			throw exception;
		} catch (SdkException exception) {
			throw new KtoSnapshotStorageException();
		}
	}

	private KtoStoredSnapshotMetadata metadata(
		KtoRawSnapshot snapshot,
		String storageKey,
		String storedHash,
		long compressedSize
	) {
		return new KtoStoredSnapshotMetadata(
			storageKey,
			storedHash,
			compressedSize,
			snapshot.capturedAt());
	}

	private byte[] gzip(byte[] payload) {
		try {
			var output = new ByteArrayOutputStream();
			try (var gzip = new GZIPOutputStream(output)) {
				gzip.write(payload);
			}
			return output.toByteArray();
		} catch (IOException exception) {
			throw new KtoSnapshotStorageException();
		}
	}

	private String uncompressedSha256(byte[] payload) {
		try (var input = new GZIPInputStream(new ByteArrayInputStream(payload))) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[8 * 1024];
			int bytesRead;
			while ((bytesRead = input.read(buffer)) != -1) {
				digest.update(buffer, 0, bytesRead);
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (IOException | NoSuchAlgorithmException exception) {
			throw new KtoSnapshotConflictException();
		}
	}

	private String sha256(byte[] payload) {
		try {
			return HexFormat.of().formatHex(
				MessageDigest.getInstance("SHA-256").digest(payload));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}
}
