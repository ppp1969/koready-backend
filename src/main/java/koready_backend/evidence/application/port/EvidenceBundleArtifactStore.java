package koready_backend.evidence.application.port;

import java.nio.file.Path;
import java.time.Instant;

public interface EvidenceBundleArtifactStore {

	StoredArtifact store(String bundleId, String fileName, Path source, String sha256, long byteSize);

	DownloadLink createDownloadUrl(String storageKey, String fileName);

	record StoredArtifact(String storageKey) {
	}

	record DownloadLink(String url, Instant expiresAt) {
	}
}
