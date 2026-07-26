package koready_backend.kto.infrastructure.snapshot;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

import koready_backend.kto.application.model.KtoRawSnapshot;

class KtoSnapshotObjectKeyFactoryTest {

	@Test
	void separatesEnglishSnapshotsUnderTheirOwnServicePrefix() throws Exception {
		byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);
		KtoRawSnapshot snapshot = new KtoRawSnapshot(
			"eng",
			"areaBasedSyncList2",
			LocalDate.of(2026, 7, 27),
			3,
			sha256(payload),
			payload,
			Instant.parse("2026-07-27T03:00:00Z"));

		String key = KtoSnapshotObjectKeyFactory.create(snapshot);

		assertTrue(key.startsWith(
			"kto/eng/areaBasedSyncList2/20260727/event-start-20260727-page-3-"));
	}

	private String sha256(byte[] payload) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
	}
}
