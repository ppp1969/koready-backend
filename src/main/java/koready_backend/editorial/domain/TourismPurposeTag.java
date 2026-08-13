package koready_backend.editorial.domain;

public enum TourismPurposeTag {
	FOOD("#음식", "#Food"),
	HISTORY("#역사", "#History"),
	TRADITION("#전통", "#Tradition"),
	ART("#예술", "#Art"),
	LOCAL("#로컬", "#Local"),
	REST("#휴식", "#Rest"),
	HEALING("#힐링", "#Healing"),
	EMOTION("#감성", "#Mood"),
	PHOTO("#사진", "#Photo"),
	WALK("#산책", "#Walk"),
	SCENERY("#풍경", "#Scenery"),
	LEISURE("#여유", "#Leisure"),
	ROMANCE("#낭만", "#Romance"),
	ADVENTURE("#모험", "#Adventure"),
	EXPERIENCE("#체험", "#Experience"),
	LEARNING("#학습", "#Learning"),
	SHOPPING("#쇼핑", "#Shopping"),
	FUN("#재미", "#Fun"),
	UNIQUE("#이색", "#Unique"),
	SEASON("#계절", "#Season"),
	KOREAN_BEAUTY("#한국미", "#KoreanBeauty"),
	INTERACTION("#교류", "#Interaction"),
	EXPLORATION("#탐방", "#Exploration"),
	IMMERSION("#몰입", "#Immersion"),
	CURIOSITY("#호기심", "#Curiosity");

	private final String labelKo;
	private final String labelEn;

	TourismPurposeTag(String labelKo, String labelEn) {
		this.labelKo = labelKo;
		this.labelEn = labelEn;
	}

	public String labelKo() {
		return labelKo;
	}

	public String labelEn() {
		return labelEn;
	}
}
