package koready_backend.kto.domain;

public record KtoPlaceClassificationInput(
	String contentTypeId,
	String classificationCode1,
	String classificationCode2,
	String classificationCode3
) {
}
