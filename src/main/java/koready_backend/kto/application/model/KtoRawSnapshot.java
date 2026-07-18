package koready_backend.kto.application.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Objects;

public record KtoRawSnapshot(
	String operation,
	LocalDate eventStartDate,
	int pageNumber,
	String rawContentSha256,
	byte[] payload,
	Instant capturedAt
) {

	public KtoRawSnapshot {
		Objects.requireNonNull(operation, "KTO snapshot operation is required");
		Objects.requireNonNull(eventStartDate, "KTO snapshot event start date is required");
		Objects.requireNonNull(rawContentSha256, "KTO snapshot raw hash is required");
		Objects.requireNonNull(payload, "KTO snapshot payload is required");
		Objects.requireNonNull(capturedAt, "KTO snapshot capture time is required");
		if (!operation.matches("[A-Za-z0-9]{1,100}")) {
			throw new IllegalArgumentException("KTO snapshot operation is invalid");
		}
		if (pageNumber < 1) {
			throw new IllegalArgumentException("KTO snapshot page number must be at least 1");
		}
		payload = payload.clone();
		if (!rawContentSha256.matches("[0-9a-f]{64}")
			|| !rawContentSha256.equals(sha256(payload))) {
			throw new IllegalArgumentException("KTO snapshot raw hash does not match its payload");
		}
	}

	@Override
	public byte[] payload() {
		return payload.clone();
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}
}
