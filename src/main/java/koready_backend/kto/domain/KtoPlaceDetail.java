package koready_backend.kto.domain;

import java.util.Objects;

public record KtoPlaceDetail(KtoPlaceItem place, String overview, String homepage) {

	public KtoPlaceDetail {
		place = Objects.requireNonNull(place, "KTO place detail is required");
		overview = normalize(overview);
		homepage = normalize(homepage);
	}

	private static String normalize(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}
}
