package koready_backend.kto.application.model;

public record KtoRelatedTourRegion(
	String areaCode,
	String signguCode,
	String providerAreaCode,
	String providerSignguCode
) {

	public KtoRelatedTourRegion {
		areaCode = requiredCode(areaCode, "area");
		signguCode = requiredCode(signguCode, "signgu");
		providerAreaCode =
			requiredCode(providerAreaCode, "provider area");
		providerSignguCode =
			requiredCode(providerSignguCode, "provider signgu");
	}

	public KtoRelatedTourRegion(
		String areaCode,
		String signguCode
	) {
		this(areaCode, signguCode, areaCode, signguCode);
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
