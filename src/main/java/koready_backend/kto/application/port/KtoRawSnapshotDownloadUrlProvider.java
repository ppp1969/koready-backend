package koready_backend.kto.application.port;

import java.time.Instant;

public interface KtoRawSnapshotDownloadUrlProvider {

	boolean available();

	DownloadLink issue(String storageKey, String fileName);

	record DownloadLink(String url, Instant expiresAt) {
	}
}
