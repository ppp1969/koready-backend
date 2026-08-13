package koready_backend.editorial.domain;

public enum EditorialJobStatus {
	NOT_REQUESTED,
	QUEUED,
	PROCESSING,
	READY,
	FAILED,
	STALE
}
