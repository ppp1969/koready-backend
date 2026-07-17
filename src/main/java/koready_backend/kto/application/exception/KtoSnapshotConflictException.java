package koready_backend.kto.application.exception;

public final class KtoSnapshotConflictException extends RuntimeException {

	public KtoSnapshotConflictException() {
		super("KTO snapshot storage key is already bound to different content");
	}
}
