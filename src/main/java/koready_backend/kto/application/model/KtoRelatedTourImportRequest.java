package koready_backend.kto.application.model;

public record KtoRelatedTourImportRequest(
	String baseYearMonth,
	String startAfterRegionKey,
	int maxRegions,
	int maxPagesPerRegion,
	boolean autoContinue
) {

	public KtoRelatedTourImportRequest {
		if (baseYearMonth == null
			|| !baseYearMonth.matches("\\d{6}")
			|| startAfterRegionKey == null
			|| (!startAfterRegionKey.isEmpty()
				&& !startAfterRegionKey.matches("\\d{2,10}:\\d{2,10}"))
			|| maxRegions < 1
			|| maxRegions > 10
			|| maxPagesPerRegion < 1
			|| maxPagesPerRegion > 50) {
			throw new IllegalArgumentException(
				"Related tour import window is invalid");
		}
	}
}
