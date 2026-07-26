package koready_backend.kto.domain;

public record KtoEnglishPlaceItem(
	String contentId,
	String oldContentId,
	String contentTypeId,
	String title,
	String address1,
	String address2,
	String primaryImageUrl,
	String thumbnailImageUrl,
	String longitude,
	String latitude,
	String modifiedTime,
	String showFlag,
	String sourceHash
) {

	public KtoEnglishPlaceItem {
		if (contentId == null || contentId.isBlank()) {
			throw new IllegalArgumentException("KTO English content id is required");
		}
		if (sourceHash == null || !sourceHash.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException("KTO English item source hash is invalid");
		}
	}
}
