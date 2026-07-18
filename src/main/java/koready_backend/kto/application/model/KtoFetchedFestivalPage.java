package koready_backend.kto.application.model;

import java.util.Objects;

import koready_backend.kto.domain.KtoFestivalPage;

public record KtoFetchedFestivalPage(
	KtoFestivalPage page,
	KtoSuccessfulCallMetadata call,
	byte[] rawPayload
) {

	public KtoFetchedFestivalPage {
		Objects.requireNonNull(page, "KTO festival page is required");
		Objects.requireNonNull(call, "KTO festival call metadata is required");
		Objects.requireNonNull(rawPayload, "KTO festival raw payload is required");
		rawPayload = rawPayload.clone();
		if (rawPayload.length != page.responseBytes()) {
			throw new IllegalArgumentException("KTO festival payload size does not match page metadata");
		}
	}

	@Override
	public byte[] rawPayload() {
		return rawPayload.clone();
	}
}
