package koready_backend.auth.infrastructure.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SecureRefreshTokenCodecTest {

	private final SecureRefreshTokenCodec codec = new SecureRefreshTokenCodec();

	@Test
	void generatesOpaqueTokensAndOnlyStableHashesNeedPersistence() {
		String first = codec.generate();
		String second = codec.generate();

		assertTrue(first.startsWith("rft_"));
		assertNotEquals(first, second);
		assertEquals(64, codec.hash(first).length());
		assertEquals(codec.hash(first), codec.hash(first));
		assertNotEquals(first, codec.hash(first));
	}
}
