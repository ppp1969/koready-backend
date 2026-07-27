package koready_backend.kto.application.model;

import java.util.Objects;

import koready_backend.kto.domain.KtoPhotoAwardPage;

public record KtoFetchedPhotoAwardPage(
	KtoPhotoAwardPage page,
	KtoSuccessfulCallMetadata call,
	byte[] rawPayload
) {

	public KtoFetchedPhotoAwardPage {
		Objects.requireNonNull(page, "Photo award page is required");
		Objects.requireNonNull(call, "Photo award call metadata is required");
		Objects.requireNonNull(rawPayload, "Photo award raw payload is required");
		rawPayload = rawPayload.clone();
	}

	@Override
	public byte[] rawPayload() {
		return rawPayload.clone();
	}
}
