package koready_backend.kto.application.port;

import koready_backend.kto.application.model.KtoRawSnapshot;
import koready_backend.kto.application.model.KtoStoredSnapshotMetadata;

public interface KtoRawSnapshotStore {

	KtoStoredSnapshotMetadata store(KtoRawSnapshot snapshot);
}
