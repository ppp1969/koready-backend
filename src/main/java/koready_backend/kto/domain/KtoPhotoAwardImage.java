package koready_backend.kto.domain;

public record KtoPhotoAwardImage(
	String contentId,
	String originImageUrl,
	String thumbnailImageUrl,
	String title,
	String copyrightType,
	boolean visible,
	int sourceOrder
) {
	public KtoPhotoAwardImage {
		if (contentId == null || contentId.isBlank() || originImageUrl == null
			|| originImageUrl.isBlank() || sourceOrder < 1) {
			throw new IllegalArgumentException("KTO photo award image is invalid");
		}
	}
}
