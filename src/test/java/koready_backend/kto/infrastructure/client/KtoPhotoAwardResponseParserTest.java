package koready_backend.kto.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class KtoPhotoAwardResponseParserTest {
	@Test
	void parsesAwardImagesAndVisibility() throws IOException {
		var parser = new KtoPhotoAwardResponseParser(JsonMapper.builder().build());
		var page = parser.parse(fixture());

		assertEquals(2, page.images().size());
		assertEquals("award-visible", page.images().getFirst().contentId());
		assertTrue(page.images().getFirst().visible());
		assertFalse(page.images().get(1).visible());
	}

	private byte[] fixture() throws IOException {
		try (var input = getClass().getResourceAsStream("/fixtures/kto/photo-award-page.json")) {
			if (input == null) throw new IOException("Fixture not found");
			return input.readAllBytes();
		}
	}
}
