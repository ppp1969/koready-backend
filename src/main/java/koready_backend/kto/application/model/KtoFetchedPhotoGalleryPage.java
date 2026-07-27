package koready_backend.kto.application.model;

import koready_backend.kto.domain.KtoPhotoGalleryPage;

public record KtoFetchedPhotoGalleryPage(
	KtoPhotoGalleryPage page,
	KtoSuccessfulCallMetadata call,
	byte[] rawPayload
) {

	public KtoFetchedPhotoGalleryPage {
		if (page == null || call == null || rawPayload == null) {
			throw new IllegalArgumentException(
				"Fetched photo gallery page is incomplete");
		}
		rawPayload = rawPayload.clone();
	}

	@Override
	public byte[] rawPayload() {
		return rawPayload.clone();
	}
}
