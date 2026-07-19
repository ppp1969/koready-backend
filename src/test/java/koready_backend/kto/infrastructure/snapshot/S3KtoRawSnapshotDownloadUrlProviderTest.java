package koready_backend.kto.infrastructure.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.kto.application.port.KtoRawSnapshotDownloadUrlProvider.DownloadLink;
import koready_backend.kto.infrastructure.config.KtoS3SnapshotProperties;
import koready_backend.kto.infrastructure.config.KtoSnapshotDownloadProperties;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3KtoRawSnapshotDownloadUrlProviderTest {

	private static final String BUCKET = "koready-kto-snapshots-test";
	private static final Instant EXPIRES_AT = Instant.parse("2026-07-19T09:05:00Z");

	@Mock
	S3Presigner presigner;

	@Mock
	PresignedGetObjectRequest presignedRequest;

	private S3KtoRawSnapshotDownloadUrlProvider provider;

	@BeforeEach
	void setUp() {
		provider = new S3KtoRawSnapshotDownloadUrlProvider(
			presigner,
			new KtoS3SnapshotProperties(
				BUCKET,
				"ap-northeast-2",
				Duration.ofSeconds(3),
				Duration.ofSeconds(10)),
			new KtoSnapshotDownloadProperties(Duration.ofMinutes(5)));
	}

	@Test
	void presignsPrivateGetForFiveMinutesWithASafeAttachmentName() throws Exception {
		when(presigner.presignGetObject(any(GetObjectPresignRequest.class)))
			.thenReturn(presignedRequest);
		when(presignedRequest.url()).thenReturn(
			URI.create("https://private-storage.example/object?signature=temporary").toURL());
		when(presignedRequest.expiration()).thenReturn(EXPIRES_AT);

		DownloadLink result = provider.issue(
			"kto/kor/searchFestival2/snapshot.json.gz",
			"koready-kto-snapshot-11.json.gz");

		ArgumentCaptor<GetObjectPresignRequest> captor =
			ArgumentCaptor.forClass(GetObjectPresignRequest.class);
		verify(presigner).presignGetObject(captor.capture());
		GetObjectPresignRequest request = captor.getValue();
		GetObjectRequest objectRequest = request.getObjectRequest();
		assertTrue(provider.available());
		assertEquals(Duration.ofMinutes(5), request.signatureDuration());
		assertEquals(BUCKET, objectRequest.bucket());
		assertEquals("kto/kor/searchFestival2/snapshot.json.gz", objectRequest.key());
		assertEquals("application/gzip", objectRequest.responseContentType());
		assertEquals(
			"attachment; filename=\"koready-kto-snapshot-11.json.gz\"",
			objectRequest.responseContentDisposition());
		assertEquals(EXPIRES_AT, result.expiresAt());
		assertEquals(
			"https://private-storage.example/object?signature=temporary",
			result.url());
	}
}
