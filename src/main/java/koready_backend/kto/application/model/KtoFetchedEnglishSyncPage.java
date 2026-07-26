package koready_backend.kto.application.model;

import java.util.Objects;

import koready_backend.kto.domain.KtoEnglishSyncPage;

public record KtoFetchedEnglishSyncPage(
	KtoEnglishSyncPage page,
	KtoSuccessfulCallMetadata call,
	byte[] rawPayload
) {

	public KtoFetchedEnglishSyncPage {
		Objects.requireNonNull(page, "KTO English sync page is required");
		Objects.requireNonNull(call, "KTO English call metadata is required");
		Objects.requireNonNull(rawPayload, "KTO English raw payload is required");
		rawPayload = rawPayload.clone();
		if (rawPayload.length != page.responseBytes()) {
			throw new IllegalArgumentException("KTO English payload size does not match page metadata");
		}
	}

	@Override
	public byte[] rawPayload() {
		return rawPayload.clone();
	}
}
