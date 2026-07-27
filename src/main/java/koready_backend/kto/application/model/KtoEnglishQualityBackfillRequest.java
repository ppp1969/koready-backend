package koready_backend.kto.application.model;

public record KtoEnglishQualityBackfillRequest(
	long startAfterSourceRecordId,
	int maxRecords,
	boolean autoContinue
) {
	public KtoEnglishQualityBackfillRequest {
		if (startAfterSourceRecordId < 0 || maxRecords < 1 || maxRecords > 200) {
			throw new IllegalArgumentException("KTO English quality backfill request is invalid");
		}
	}
}
