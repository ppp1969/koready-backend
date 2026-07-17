package koready_backend.kto.domain;

public record KtoPlaceItem(
	String contentId,
	String contentTypeId,
	String title,
	String address1,
	String address2,
	String areaCode,
	String districtCode,
	String categoryCode1,
	String categoryCode2,
	String categoryCode3,
	String copyrightType,
	String createdTime,
	String primaryImageUrl,
	String thumbnailImageUrl,
	String longitude,
	String latitude,
	String mapLevel,
	String modifiedTime,
	String phoneNumber,
	String postalCode,
	String showFlag,
	String legalDongRegionCode,
	String legalDongDistrictCode,
	String classificationCode1,
	String classificationCode2,
	String classificationCode3
) {

	public KtoPlaceItem {
		if (contentId == null || contentId.isBlank()) {
			throw new IllegalArgumentException("KTO content id is required");
		}
	}

	public boolean visible() {
		return "1".equals(showFlag);
	}
}
