package koready_backend.kto.infrastructure.snapshot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.kto.application.exception.KtoSnapshotConflictException;
import koready_backend.kto.application.exception.KtoSnapshotStorageException;
import koready_backend.kto.application.model.KtoRawSnapshot;
import koready_backend.kto.application.model.KtoStoredSnapshotMetadata;
import koready_backend.kto.infrastructure.config.KtoS3SnapshotProperties;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class S3KtoRawSnapshotStoreTest {

	private static final String BUCKET = "koready-kto-snapshots-test";
	private static final byte[] PAYLOAD = "{\"response\":{\"body\":{}}}".getBytes();

	@Mock
	S3Client s3Client;

	private S3KtoRawSnapshotStore store;

	@BeforeEach
	void setUp() {
		store = new S3KtoRawSnapshotStore(
			s3Client,
			new KtoS3SnapshotProperties(
				BUCKET,
				"ap-northeast-2",
				Duration.ofSeconds(3),
				Duration.ofSeconds(10)));
	}

	@Test
	void storesCompressedContentWithAConditionalWrite() throws Exception {
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
			.thenReturn(PutObjectResponse.builder().eTag("etag").build());

		KtoStoredSnapshotMetadata result = store.store(snapshot(PAYLOAD));

		ArgumentCaptor<PutObjectRequest> requestCaptor =
			ArgumentCaptor.forClass(PutObjectRequest.class);
		ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
		verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());
		PutObjectRequest request = requestCaptor.getValue();
		byte[] storedBytes;
		try (var input = bodyCaptor.getValue().contentStreamProvider().newStream()) {
			storedBytes = input.readAllBytes();
		}

		assertEquals(BUCKET, request.bucket());
		assertEquals(result.storageKey(), request.key());
		assertEquals("*", request.ifNoneMatch());
		assertEquals("application/gzip", request.contentType());
		assertEquals(sha256(PAYLOAD), request.metadata().get("raw-sha256"));
		assertEquals(sha256(storedBytes), request.metadata().get("stored-sha256"));
		assertEquals(storedBytes.length, result.compressedByteSize());
		assertEquals(sha256(storedBytes), result.storedObjectSha256());
		assertArrayEquals(PAYLOAD, gunzip(storedBytes));
	}

	@Test
	void replaysAnExistingObjectWhenItsBytesMatch() throws Exception {
		byte[] storedBytes = gzip(PAYLOAD);
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
			.thenThrow(preconditionFailed());
		when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
			.thenReturn(ResponseBytes.fromByteArray(
				GetObjectResponse.builder().contentLength((long) storedBytes.length).build(),
				storedBytes));

		KtoStoredSnapshotMetadata result = store.store(snapshot(PAYLOAD));

		assertEquals(sha256(storedBytes), result.storedObjectSha256());
		assertEquals(storedBytes.length, result.compressedByteSize());
		verify(s3Client).getObjectAsBytes(any(GetObjectRequest.class));
	}

	@Test
	void rejectsAnExistingObjectWhoseContentDoesNotMatch() throws Exception {
		byte[] differentBytes = gzip("{\"different\":true}".getBytes());
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
			.thenThrow(preconditionFailed());
		when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
			.thenReturn(ResponseBytes.fromByteArray(
				GetObjectResponse.builder().contentLength((long) differentBytes.length).build(),
				differentBytes));

		assertThrows(
			KtoSnapshotConflictException.class,
			() -> store.store(snapshot(PAYLOAD)));
	}

	@Test
	void retriesAConcurrentConditionalWriteConflict() throws Exception {
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
			.thenThrow(s3Exception(409, "conflict"))
			.thenReturn(PutObjectResponse.builder().eTag("etag").build());

		store.store(snapshot(PAYLOAD));

		verify(s3Client, times(2))
			.putObject(any(PutObjectRequest.class), any(RequestBody.class));
	}

	@Test
	void convertsTransportFailuresToTheStorageBoundaryException() throws Exception {
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
			.thenThrow(SdkClientException.builder().message("credentials unavailable").build());

		assertThrows(
			KtoSnapshotStorageException.class,
			() -> store.store(snapshot(PAYLOAD)));
	}

	private KtoRawSnapshot snapshot(byte[] payload) throws Exception {
		return new KtoRawSnapshot(
			"searchFestival2",
			LocalDate.of(2026, 7, 1),
			3,
			sha256(payload),
			payload,
			Instant.parse("2026-07-18T03:00:02Z"));
	}

	private S3Exception preconditionFailed() {
		return s3Exception(412, "already exists");
	}

	private S3Exception s3Exception(int statusCode, String message) {
		S3Exception.Builder builder = S3Exception.builder();
		builder.statusCode(statusCode);
		builder.message(message);
		return (S3Exception) builder.build();
	}

	private byte[] gzip(byte[] payload) throws Exception {
		var output = new ByteArrayOutputStream();
		try (var gzip = new GZIPOutputStream(output)) {
			gzip.write(payload);
		}
		return output.toByteArray();
	}

	private byte[] gunzip(byte[] payload) throws Exception {
		try (var input = new GZIPInputStream(new ByteArrayInputStream(payload))) {
			return input.readAllBytes();
		}
	}

	private String sha256(byte[] payload) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
	}
}
