package koready_backend.evidence.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.evidence.application.port.EvidenceBundleContentSource;
import koready_backend.evidence.application.port.EvidenceBundleContentSource.CallRow;
import koready_backend.evidence.application.port.EvidenceBundleContentSource.DataQualityRow;
import koready_backend.evidence.application.port.EvidenceBundleContentSource.SnapshotRow;
import koready_backend.evidence.application.port.EvidenceRawSnapshotReader;
import koready_backend.evidence.application.port.EvidenceBundleRepository.BundleRecord;
import koready_backend.evidence.domain.EvidenceBundleStatus;
import koready_backend.externalapi.domain.ExternalApiProvider;
import koready_backend.externalapi.domain.SnapshotRetentionClass;
import koready_backend.externalapi.domain.SnapshotStorageFormat;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class EvidenceBundleArchiveGeneratorTest {

	private static final Instant NOW = Instant.parse("2026-07-19T09:00:00Z");

	@Mock EvidenceBundleContentSource contentSource;
	@Mock EvidenceRawSnapshotReader snapshotReader;

	@Test
	void streamsRequiredSafeFilesAndSelectedKtoSnapshotsIntoZip() throws Exception {
		SnapshotRow snapshot = new SnapshotRow(
			11, "kto/KOR/searchFestival2/11.json.gz", SnapshotStorageFormat.JSON_GZIP,
			"a".repeat(64), "b".repeat(64), 3, NOW.minusSeconds(10),
			SnapshotRetentionClass.COMPETITION_EVIDENCE, NOW.plusSeconds(3600));
		when(contentSource.findCalls(any(), any(Long.class), any(Integer.class)))
			.thenReturn(List.of(new CallRow(
				7, ExternalApiProvider.KTO, "KOR", "searchFestival2", NOW.minusSeconds(20),
				NOW.minusSeconds(19), 1000L, true, 200, "0000", 1, 128L, snapshot)));
		when(contentSource.findBatchJobs(any(), any(), any(Long.class), any(Integer.class)))
			.thenReturn(List.of());
		when(contentSource.findSyncCursors(any())).thenReturn(List.of());
		when(contentSource.dataQuality()).thenReturn(new DataQualityRow(10, 8, 6));
		when(snapshotReader.open(snapshot.storageKey())).thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));

		EvidenceBundleArchiveGenerator generator = new EvidenceBundleArchiveGenerator(
			contentSource, snapshotReader, JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC));
		var archive = generator.generate(bundle());

		try (ZipFile zip = new ZipFile(archive.path().toFile())) {
			assertTrue(zip.getEntry("manifest.json") != null);
			assertTrue(zip.getEntry("open_api_calls.csv") != null);
			assertTrue(zip.getEntry("batch_jobs.csv") != null);
			assertTrue(zip.getEntry("sync_cursors.json") != null);
			assertTrue(zip.getEntry("data_quality_summary.json") != null);
			assertTrue(zip.getEntry("raw_snapshots/KTO/searchFestival2/11.json.gz") != null);
			assertTrue(zip.getEntry("SHA256SUMS") != null);
			String manifest = new String(zip.getInputStream(zip.getEntry("manifest.json")).readAllBytes());
			assertTrue(manifest.contains("rawContentSha256"));
			assertTrue(manifest.contains("storedObjectSha256"));
		}
		assertEquals(1, archive.callCount());
		assertEquals(1, archive.rawSnapshotCount());
		assertTrue(archive.manifestFiles().stream().allMatch(file -> file.byteSize() > 0));
		assertTrue(archive.sha256().matches("[a-f0-9]{64}"));
		Files.deleteIfExists(archive.path());
	}

	private static BundleRecord bundle() {
		return new BundleRecord(
			1, "evidence_01J2ABCDEF", "2026 공모전 OpenAPI 사용 증빙", EvidenceBundleStatus.RUNNING,
			NOW.minusSeconds(3600), NOW, List.of(ExternalApiProvider.KTO), List.of(), true, 3,
			null, null, null, null, null, null, "admin-1", NOW.minusSeconds(60), NOW,
			null, null, List.of(), List.of());
	}
}
