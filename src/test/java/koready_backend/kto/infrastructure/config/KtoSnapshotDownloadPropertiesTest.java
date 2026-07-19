package koready_backend.kto.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class KtoSnapshotDownloadPropertiesTest {

	@Test
	void defaultsToFiveMinutes() {
		assertEquals(
			Duration.ofMinutes(5),
			new KtoSnapshotDownloadProperties(null).requiredExpiration());
	}

	@Test
	void restrictsSignedUrlsToOneThroughFifteenMinutes() {
		assertEquals(
			Duration.ofMinutes(1),
			new KtoSnapshotDownloadProperties(Duration.ofMinutes(1))
				.requiredExpiration());
		assertEquals(
			Duration.ofMinutes(15),
			new KtoSnapshotDownloadProperties(Duration.ofMinutes(15))
				.requiredExpiration());
		assertThrows(IllegalStateException.class, () ->
			new KtoSnapshotDownloadProperties(Duration.ofSeconds(59))
				.requiredExpiration());
		assertThrows(IllegalStateException.class, () ->
			new KtoSnapshotDownloadProperties(Duration.ofMinutes(16))
				.requiredExpiration());
	}
}
