package koready_backend.kto.application.exception;

public final class KtoSnapshotStorageException extends RuntimeException {

	public KtoSnapshotStorageException() {
		super("KTO snapshot could not be stored");
	}
}
