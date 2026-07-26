package koready_backend.kto.application.model;

import java.util.Objects;

import koready_backend.kto.domain.KtoDetailOperationResponse;

public record KtoFetchedDetailOperation(
	KtoDetailOperationResponse response,
	KtoSuccessfulCallMetadata call,
	byte[] rawPayload
) {

	public KtoFetchedDetailOperation {
		response = Objects.requireNonNull(response, "KTO detail response is required");
		call = Objects.requireNonNull(call, "KTO detail call metadata is required");
		Objects.requireNonNull(rawPayload, "KTO detail raw payload is required");
		rawPayload = rawPayload.clone();
		if (rawPayload.length != response.responseBytes()) {
			throw new IllegalArgumentException(
				"KTO detail payload size does not match response metadata");
		}
	}

	@Override
	public byte[] rawPayload() {
		return rawPayload.clone();
	}
}
