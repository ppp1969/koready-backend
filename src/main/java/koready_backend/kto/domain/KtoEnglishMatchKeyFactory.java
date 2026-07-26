package koready_backend.kto.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

public final class KtoEnglishMatchKeyFactory {

	private static final Map<String, String> ENGLISH_TO_KOREAN_CONTENT_TYPES = Map.of(
		"75", "25",
		"76", "12",
		"77", "28",
		"78", "14",
		"79", "38",
		"80", "32",
		"82", "39",
		"85", "15");

	private KtoEnglishMatchKeyFactory() {
	}

	public static String imagePathHash(String imageUrl) {
		if (imageUrl == null || imageUrl.isBlank()) {
			return null;
		}
		try {
			String path = URI.create(imageUrl.strip()).getRawPath();
			if (path == null || path.isBlank()) {
				return null;
			}
			String normalized = path.replaceAll("/{2,}", "/").toLowerCase(Locale.ROOT);
			return sha256(normalized);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	public static String englishCoordinateContentTypeKey(
		String longitude,
		String latitude,
		String englishContentTypeId
	) {
		return coordinateContentTypeKey(
			longitude,
			latitude,
			ENGLISH_TO_KOREAN_CONTENT_TYPES.get(englishContentTypeId));
	}

	public static String koreanCoordinateContentTypeKey(
		String longitude,
		String latitude,
		String koreanContentTypeId
	) {
		return coordinateContentTypeKey(longitude, latitude, koreanContentTypeId);
	}

	private static String coordinateContentTypeKey(
		String longitude,
		String latitude,
		String contentTypeId
	) {
		if (longitude == null || latitude == null || contentTypeId == null || contentTypeId.isBlank()) {
			return null;
		}
		try {
			BigDecimal x = new BigDecimal(longitude).setScale(6, RoundingMode.HALF_UP);
			BigDecimal y = new BigDecimal(latitude).setScale(6, RoundingMode.HALF_UP);
			if (x.compareTo(BigDecimal.valueOf(-180)) < 0
				|| x.compareTo(BigDecimal.valueOf(180)) > 0
				|| y.compareTo(BigDecimal.valueOf(-90)) < 0
				|| y.compareTo(BigDecimal.valueOf(90)) > 0) {
				return null;
			}
			return x.toPlainString() + "|" + y.toPlainString() + "|" + contentTypeId.strip();
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(
				MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}
}
