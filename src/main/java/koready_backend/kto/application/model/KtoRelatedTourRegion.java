package koready_backend.kto.application.model;

public record KtoRelatedTourRegion(
	String areaCode,
	String signguCode
) {

	public KtoRelatedTourRegion {
		areaCode = requiredCode(areaCode, "area");
		signguCode = requiredCode(signguCode, "signgu");
	}

	public String key() {
		return areaCode + ":" + signguCode;
	}

	private static String requiredCode(String value, String field) {
		if (value == null || !value.matches("\\d{2,10}")) {
			throw new IllegalArgumentException(
				"Related tour " + field + " code is invalid");
		}
		return value;
	}
}
