package koready_backend.kto.domain;

public enum KtoDetailOperation {
	COMMON("detailCommon2", false),
	INTRO("detailIntro2", true),
	INFO("detailInfo2", true),
	IMAGE("detailImage2", false);

	private final String apiName;
	private final boolean contentTypeRequired;

	KtoDetailOperation(String apiName, boolean contentTypeRequired) {
		this.apiName = apiName;
		this.contentTypeRequired = contentTypeRequired;
	}

	public String apiName() {
		return apiName;
	}

	public boolean contentTypeRequired() {
		return contentTypeRequired;
	}
}
