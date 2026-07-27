package koready_backend.kto.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import koready_backend.kto.application.exception.KtoResponseParseException;
import koready_backend.kto.domain.KtoPhotoGalleryPage;
import tools.jackson.databind.json.JsonMapper;

class KtoPhotoGalleryResponseParserTest {

	private final KtoPhotoGalleryResponseParser parser =
		new KtoPhotoGalleryResponseParser(JsonMapper.builder().build());

	@Test
	void parsesGalleryMetadataAndNormalizesBlankFields() throws IOException {
		byte[] payload = fixture();

		KtoPhotoGalleryPage page = parser.parse(payload);

		assertEquals(1, page.pageNumber());
		assertEquals(200, page.pageSize());
		assertEquals(312, page.totalCount());
		assertEquals(2, page.items().size());
		assertEquals("gallery-001", page.items().getFirst().contentId());
		assertEquals("10", page.items().getFirst().photographyMonth());
		assertEquals("KTO", page.items().getFirst().photographer());
		assertEquals(
			"https://example.invalid/photo-gallery/gallery-001.jpg",
			page.items().getFirst().imageUrl());
		assertNull(page.items().get(1).photographyMonth());
		assertNull(page.items().get(1).photographer());
		assertEquals(payload.length, page.responseBytes());
	}

	@Test
	void rejectsAnItemWithoutAValidWebImage() throws IOException {
		String payload = new String(fixture(), StandardCharsets.UTF_8)
			.replace(
				"https://example.invalid/photo-gallery/gallery-001.jpg",
				"javascript:alert(1)");

		assertThrows(
			KtoResponseParseException.class,
			() -> parser.parse(payload.getBytes(StandardCharsets.UTF_8)));
	}

	private byte[] fixture() throws IOException {
		try (var input = getClass().getResourceAsStream(
			"/fixtures/kto/photo-gallery-page.json")) {
			if (input == null) {
				throw new IOException("Fixture not found");
			}
			return input.readAllBytes();
		}
	}
}
