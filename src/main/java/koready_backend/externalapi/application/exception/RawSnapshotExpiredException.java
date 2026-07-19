package koready_backend.externalapi.application.exception;

public final class RawSnapshotExpiredException extends RuntimeException {

	public RawSnapshotExpiredException(long snapshotId) {
		super("Raw snapshot has expired: " + snapshotId);
	}
}
