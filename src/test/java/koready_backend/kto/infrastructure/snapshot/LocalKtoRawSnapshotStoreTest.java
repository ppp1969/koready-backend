package koready_backend.kto.infrastructure.snapshot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import koready_backend.kto.application.exception.KtoSnapshotConflictException;
import koready_backend.kto.application.exception.KtoSnapshotStorageException;
import koready_backend.kto.application.model.KtoRawSnapshot;
import koready_backend.kto.application.model.KtoStoredSnapshotMetadata;
import koready_backend.kto.infrastructure.config.KtoSnapshotProperties;

class LocalKtoRawSnapshotStoreTest {

	@TempDir
	Path directory;

	@Test
	void storesAndReplaysAGzipSnapshotOutsideTheRepository() throws Exception {
		byte[] payload = "{\"response\":{\"body\":{}}}".getBytes();
		KtoRawSnapshot snapshot = snapshot(payload);
		LocalKtoRawSnapshotStore store = new LocalKtoRawSnapshotStore(
			new KtoSnapshotProperties(directory));

		KtoStoredSnapshotMetadata first = store.store(snapshot);
		KtoStoredSnapshotMetadata replay = store.store(snapshot);

		assertEquals(first.storageKey(), replay.storageKey());
		assertEquals(first.storedObjectSha256(), replay.storedObjectSha256());
		assertFalse(first.storageKey().contains("serviceKey"));
		assertFalse(first.storageKey().contains("secret"));
		Path storedFile = directory.resolve(first.storageKey());
		try (var input = new GZIPInputStream(Files.newInputStream(storedFile))) {
			assertArrayEquals(payload, input.readAllBytes());
		}
		try (var files = Files.walk(directory)) {
			assertEquals(1, files.filter(Files::isRegularFile).count());
		}
	}

	@Test
	void rejectsAFileWhoseUncompressedContentNoLongerMatches() throws Exception {
		byte[] payload = "{\"response\":{}}".getBytes();
		KtoRawSnapshot snapshot = snapshot(payload);
		LocalKtoRawSnapshotStore store = new LocalKtoRawSnapshotStore(
			new KtoSnapshotProperties(directory));
		KtoStoredSnapshotMetadata metadata = store.store(snapshot);
		Files.writeString(directory.resolve(metadata.storageKey()), "tampered");

		assertThrows(KtoSnapshotConflictException.class, () -> store.store(snapshot));
	}

	@Test
	void rejectsASnapshotDirectoryInsideTheWorkingTree() throws Exception {
		LocalKtoRawSnapshotStore store = new LocalKtoRawSnapshotStore(
			new KtoSnapshotProperties(Path.of("build", "unsafe-kto-snapshots")));

		assertThrows(
			KtoSnapshotStorageException.class,
			() -> store.store(snapshot("{}".getBytes())));
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

	private String sha256(byte[] payload) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
	}
}
