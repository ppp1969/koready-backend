package koready_backend.externalapi.application.exception;

public final class ProviderRetentionRestrictedException extends RuntimeException {

	public ProviderRetentionRestrictedException(long snapshotId) {
		super("Provider retention policy restricts snapshot download: " + snapshotId);
	}
}
