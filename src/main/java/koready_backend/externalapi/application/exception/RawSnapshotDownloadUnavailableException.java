package koready_backend.externalapi.application.exception;

public final class RawSnapshotDownloadUnavailableException extends RuntimeException {

	public RawSnapshotDownloadUnavailableException() {
		super("Raw snapshot download is temporarily unavailable.");
	}
}
