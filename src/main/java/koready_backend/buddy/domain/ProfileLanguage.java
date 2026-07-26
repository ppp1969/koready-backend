package koready_backend.buddy.domain;

public enum ProfileLanguage {
	KO("한국어", "Korean", 1),
	EN("영어", "English", 2),
	ZH("중국어", "Chinese", 3),
	JA("일본어", "Japanese", 4),
	VI("베트남어", "Vietnamese", 5),
	TH("태국어", "Thai", 6),
	MN("몽골어", "Mongolian", 7),
	RU("러시아어", "Russian", 8),
	ID("인도네시아어", "Indonesian", 9),
	ES("스페인어", "Spanish", 10),
	FR("프랑스어", "French", 11),
	DE("독일어", "German", 12),
	AR("아랍어", "Arabic", 13);

	private final String labelKo;
	private final String labelEn;
	private final int displayOrder;

	ProfileLanguage(String labelKo, String labelEn, int displayOrder) {
		this.labelKo = labelKo;
		this.labelEn = labelEn;
		this.displayOrder = displayOrder;
	}

	public String labelKo() {
		return labelKo;
	}

	public String labelEn() {
		return labelEn;
	}

	public int displayOrder() {
		return displayOrder;
	}
}
