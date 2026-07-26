package koready_backend.kto.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class KtoEnglishApiPropertiesTest {

	@Test
	void onlyAllowsTheApprovedEnglishTourApiEndpoint() {
		assertThrows(IllegalArgumentException.class, () -> properties(
			URI.create("https://example.invalid/B551011/EngService2")));
		assertThrows(IllegalArgumentException.class, () -> properties(
			URI.create("https://apis.data.go.kr/B551011/KorService2")));
	}

	private KtoEnglishApiProperties properties(URI baseUrl) {
		return new KtoEnglishApiProperties(
			baseUrl,
			"test-key",
			4 * 1024 * 1024,
			Duration.ofSeconds(3),
			Duration.ofSeconds(10),
			"ETC",
			"KoReady");
	}
}
