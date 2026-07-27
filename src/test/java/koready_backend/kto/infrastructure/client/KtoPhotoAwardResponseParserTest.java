package koready_backend.kto.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import koready_backend.kto.application.exception.KtoResponseParseException;
import koready_backend.kto.domain.KtoPhotoAwardPage;
import tools.jackson.databind.json.JsonMapper;

class KtoPhotoAwardResponseParserTest {

	private final KtoPhotoAwardResponseParser parser =
		new KtoPhotoAwardResponseParser(JsonMapper.builder().build());

	@Test
	void parsesAllBilingualAwardMetadataAndNormalizesBlankFields() throws IOException {
		byte[] payload = fixture();

		KtoPhotoAwardPage page = parser.parse(payload);

		assertEquals(1, page.pageNumber());
		assertEquals(200, page.pageSize());
		assertEquals(96, page.totalCount());
		assertEquals(2, page.items().size());
		assertEquals("award-001", page.items().getFirst().contentId());
		assertEquals("궁궐의 아침", page.items().getFirst().titleKo());
		assertEquals("Gyeongbokgung Palace, Seoul",
			page.items().getFirst().filmLocationEn());
		assertEquals("https://example.invalid/photo-award/original-001.jpg",
			page.items().getFirst().originalImageUrl());
		assertNull(page.items().get(1).thumbnailImageUrl());
		assertEquals(payload.length, page.responseBytes());
	}

	@Test
	void rejectsAnAwardWithoutAnOriginalImage() throws IOException {
		String payload = new String(fixture())
			.replace("\"orgImage\": \"https://example.invalid/photo-award/original-001.jpg\",", "");

		assertThrows(
			KtoResponseParseException.class,
			() -> parser.parse(payload.getBytes()));
	}

	private byte[] fixture() throws IOException {
		try (var input = getClass().getResourceAsStream(
			"/fixtures/kto/photo-award-page.json")) {
			if (input == null) {
				throw new IOException("Fixture not found");
			}
			return input.readAllBytes();
		}
	}
}
