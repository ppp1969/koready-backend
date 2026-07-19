package koready_backend.externalapi.application.exception;

public final class SyncCursorNotFoundException extends RuntimeException {

	public SyncCursorNotFoundException(long cursorId) {
		super("Sync cursor " + cursorId + " was not found.");
	}
}
