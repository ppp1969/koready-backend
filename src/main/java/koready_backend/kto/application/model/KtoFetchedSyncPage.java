package koready_backend.kto.application.model;

import java.util.Objects;

import koready_backend.kto.domain.KtoSyncPage;

public record KtoFetchedSyncPage(
	KtoSyncPage page,
	KtoSuccessfulCallMetadata call,
	byte[] rawPayload
) {

	public KtoFetchedSyncPage {
		Objects.requireNonNull(page, "KTO sync page is required");
		Objects.requireNonNull(call, "KTO sync call metadata is required");
		Objects.requireNonNull(rawPayload, "KTO sync raw payload is required");
		rawPayload = rawPayload.clone();
		if (rawPayload.length != page.responseBytes()) {
			throw new IllegalArgumentException("KTO sync payload size does not match page metadata");
		}
	}

	@Override
	public byte[] rawPayload() {
		return rawPayload.clone();
	}
}
