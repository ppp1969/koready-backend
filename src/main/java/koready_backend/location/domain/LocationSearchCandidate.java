package koready_backend.location.domain;

import java.util.Objects;

public record LocationSearchCandidate(
	LocationSearchResultType resultType,
	String providerPlaceId,
	String name,
	String roadAddress,
	String address,
	double latitude,
	double longitude,
	String sido,
	String sigungu,
	String dong
) {

	public LocationSearchCandidate {
		Objects.requireNonNull(resultType, "Location result type is required");
		name = required(name, 200, "Location name");
		sido = required(sido, 100, "Location sido");
		sigungu = required(sigungu, 100, "Location sigungu");
		providerPlaceId = nullable(providerPlaceId, 191, "Location provider ID");
		roadAddress = nullable(roadAddress, 500, "Location road address");
		address = nullable(address, 500, "Location address");
		dong = nullable(dong, 100, "Location dong");
		if (roadAddress == null && address == null) {
			throw new IllegalArgumentException("A location address is required");
		}
		if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
			|| !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
			throw new IllegalArgumentException("Location coordinates are invalid");
		}
	}

	private static String required(String value, int maxLength, String name) {
		String normalized = nullable(value, maxLength, name);
		if (normalized == null) {
			throw new IllegalArgumentException(name + " is required");
		}
		return normalized;
	}

	private static String nullable(String value, int maxLength, String name) {
		if (value == null) {
			return null;
		}
		String normalized = value.strip();
		if (normalized.length() > maxLength) {
			throw new IllegalArgumentException(name + " is too long");
		}
		return normalized.isEmpty() ? null : normalized;
	}
}
