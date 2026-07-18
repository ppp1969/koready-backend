package koready_backend.externalapi.application.exception;

public final class RawSnapshotNotFoundException extends RuntimeException {

	public RawSnapshotNotFoundException(long snapshotId) {
		super("Raw snapshot " + snapshotId + " was not found.");
	}
}
