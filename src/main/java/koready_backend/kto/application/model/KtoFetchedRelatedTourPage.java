package koready_backend.kto.application.model;

import koready_backend.kto.domain.KtoRelatedTourPage;

public record KtoFetchedRelatedTourPage(
	KtoRelatedTourPage page,
	KtoSuccessfulCallMetadata call,
	byte[] rawPayload
) {

	public KtoFetchedRelatedTourPage {
		if (page == null || call == null || rawPayload == null) {
			throw new IllegalArgumentException(
				"Fetched related tour page is incomplete");
		}
		rawPayload = rawPayload.clone();
	}

	@Override
	public byte[] rawPayload() {
		return rawPayload.clone();
	}
}
