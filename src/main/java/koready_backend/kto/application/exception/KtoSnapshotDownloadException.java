package koready_backend.kto.application.exception;

public final class KtoSnapshotDownloadException extends RuntimeException {

	public KtoSnapshotDownloadException() {
		super("KTO snapshot download URL could not be created.");
	}
}
