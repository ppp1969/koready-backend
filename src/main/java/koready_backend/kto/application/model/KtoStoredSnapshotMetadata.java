package koready_backend.kto.application.model;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public record KtoStoredSnapshotMetadata(
	String storageKey,
	String storedObjectSha256,
	long compressedByteSize,
	Instant capturedAt
) {

	private static final int MAX_STORAGE_KEY_LENGTH = 500;

	public KtoStoredSnapshotMetadata {
		Objects.requireNonNull(storageKey, "KTO snapshot storage key is required");
		Objects.requireNonNull(storedObjectSha256, "KTO stored object hash is required");
		Objects.requireNonNull(capturedAt, "KTO snapshot capture time is required");

		String normalizedKey = storageKey.toLowerCase(Locale.ROOT);
		if (!storageKey.startsWith("kto/")
			|| !storageKey.endsWith(".json.gz")
			|| storageKey.length() > MAX_STORAGE_KEY_LENGTH
			|| normalizedKey.contains("servicekey")
			|| normalizedKey.contains("secret")
			|| normalizedKey.contains("token")
			|| storageKey.contains("?")
			|| storageKey.contains("..")) {
			throw new IllegalArgumentException("KTO snapshot storage key is unsafe");
		}
		if (!storedObjectSha256.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException("KTO stored object hash is invalid");
		}
		if (compressedByteSize < 0) {
			throw new IllegalArgumentException("KTO compressed snapshot size must not be negative");
		}
	}
}
