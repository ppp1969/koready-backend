package koready_backend.buddy.infrastructure.storage;

import java.time.Instant;
import java.util.Map;

import jakarta.annotation.PreDestroy;
import koready_backend.buddy.application.exception.ProfileImageStorageUnavailableException;
import koready_backend.buddy.application.port.ProfileImageStorage;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

final class S3ProfileImageStorage implements ProfileImageStorage {

	private final S3Client client;
	private final S3Presigner presigner;
	private final ProfileImageStorageProperties properties;

	S3ProfileImageStorage(
		S3Client client,
		S3Presigner presigner,
		ProfileImageStorageProperties properties
	) {
		this.client = client;
		this.presigner = presigner;
		this.properties = properties;
	}

	@Override
	public UploadTarget createUpload(
		String objectKey,
		String contentType,
		Instant now
	) {
		try {
			var put = PutObjectRequest.builder()
				.bucket(properties.requiredBucket())
				.key(objectKey)
				.contentType(contentType)
				.build();
			var request = PutObjectPresignRequest.builder()
				.signatureDuration(properties.requiredUploadExpiration())
				.putObjectRequest(put)
				.build();
			var signed = presigner.presignPutObject(request);
			return new UploadTarget(
				signed.url().toString(),
				now.plus(properties.requiredUploadExpiration()),
				Map.of("Content-Type", contentType));
		} catch (SdkException exception) {
			throw new ProfileImageStorageUnavailableException(exception);
		}
	}

	@Override
	public StoredObject inspect(String objectKey) {
		try {
			var response = client.headObject(HeadObjectRequest.builder()
				.bucket(properties.requiredBucket())
				.key(objectKey)
				.build());
			byte[] signature = client.getObjectAsBytes(
				GetObjectRequest.builder()
					.bucket(properties.requiredBucket())
					.key(objectKey)
					.range("bytes=0-31")
					.build())
				.asByteArray();
			return new StoredObject(
				response.contentType(), response.contentLength(), signature);
		} catch (SdkException exception) {
			throw new ProfileImageStorageUnavailableException(exception);
		}
	}

	@Override
	public void delete(String objectKey) {
		try {
			client.deleteObject(DeleteObjectRequest.builder()
				.bucket(properties.requiredBucket())
				.key(objectKey)
				.build());
		} catch (SdkException exception) {
			throw new ProfileImageStorageUnavailableException(exception);
		}
	}

	@Override
	public String createViewUrl(String objectKey, Instant now) {
		try {
			var get = GetObjectRequest.builder()
				.bucket(properties.requiredBucket())
				.key(objectKey)
				.build();
			return presigner.presignGetObject(GetObjectPresignRequest.builder()
					.signatureDuration(properties.requiredViewExpiration())
					.getObjectRequest(get)
					.build())
				.url()
				.toString();
		} catch (SdkException exception) {
			throw new ProfileImageStorageUnavailableException(exception);
		}
	}

	@PreDestroy
	void close() {
		presigner.close();
		client.close();
	}
}
