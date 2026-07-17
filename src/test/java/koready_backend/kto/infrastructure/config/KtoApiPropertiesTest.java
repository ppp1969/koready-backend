package koready_backend.kto.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class KtoApiPropertiesTest {

	@Test
	void acceptsTheMemoryBoundedDefaults() {
		KtoApiProperties properties = properties(4 * 1024 * 1024, Duration.ofSeconds(3), Duration.ofSeconds(10));

		assertEquals(4 * 1024 * 1024, properties.maxResponseBytes());
		assertEquals(Duration.ofSeconds(3), properties.connectTimeout());
		assertEquals(Duration.ofSeconds(10), properties.readTimeout());
	}

	@Test
	void rejectsInvalidResponseAndTimeoutLimits() {
		assertThrows(IllegalArgumentException.class,
			() -> properties(0, Duration.ofSeconds(3), Duration.ofSeconds(10)));
		assertThrows(IllegalArgumentException.class,
			() -> properties(16 * 1024 * 1024 + 1, Duration.ofSeconds(3), Duration.ofSeconds(10)));
		assertThrows(IllegalArgumentException.class,
			() -> properties(1024, Duration.ZERO, Duration.ofSeconds(10)));
		assertThrows(IllegalArgumentException.class,
			() -> properties(1024, Duration.ofSeconds(3), Duration.ZERO));
	}

	@Test
	void rejectsAnUnsafeOrIncompleteEndpoint() {
		assertThrows(IllegalArgumentException.class,
			() -> new KtoApiProperties(
				URI.create("http://apis.data.go.kr/B551011/KorService2"),
				"",
				1024,
				Duration.ofSeconds(3),
				Duration.ofSeconds(10),
				"ETC",
				"KoReady"));
		assertThrows(IllegalArgumentException.class,
			() -> new KtoApiProperties(
				URI.create("https://example.invalid/B551011/KorService2"),
				"",
				1024,
				Duration.ofSeconds(3),
				Duration.ofSeconds(10),
				"ETC",
				"KoReady"));
		assertThrows(IllegalArgumentException.class,
			() -> new KtoApiProperties(
				URI.create("https://apis.data.go.kr"),
				"",
				1024,
				Duration.ofSeconds(3),
				Duration.ofSeconds(10),
				"ETC",
				"KoReady"));
	}

	private KtoApiProperties properties(int maxResponseBytes, Duration connectTimeout, Duration readTimeout) {
		return new KtoApiProperties(
			URI.create("https://apis.data.go.kr/B551011/KorService2"),
			"",
			maxResponseBytes,
			connectTimeout,
			readTimeout,
			"ETC",
			"KoReady");
	}
}
