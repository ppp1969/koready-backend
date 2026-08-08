package koready_backend.kto.application.model;

public record KtoClassificationApplyResult(
	String ruleVersion,
	long processedPlaces,
	long classifiedPlaces,
	long unclassifiedPlaces,
	long automaticMappings,
	long lastPlaceId,
	boolean completed
) {
}
