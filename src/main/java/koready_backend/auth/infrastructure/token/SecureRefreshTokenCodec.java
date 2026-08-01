package koready_backend.auth.infrastructure.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import koready_backend.auth.application.port.RefreshTokenCodec;

public final class SecureRefreshTokenCodec implements RefreshTokenCodec {

	private static final int TOKEN_BYTES = 32;
	private final SecureRandom secureRandom = new SecureRandom();

	@Override
	public String generate() {
		byte[] bytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return "rft_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	@Override
	public String hash(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}
}
