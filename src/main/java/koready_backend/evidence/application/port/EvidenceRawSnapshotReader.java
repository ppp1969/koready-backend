package koready_backend.evidence.application.port;

import java.io.InputStream;

public interface EvidenceRawSnapshotReader {

	InputStream open(String storageKey);
}
