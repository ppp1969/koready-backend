package koready_backend.kto.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import koready_backend.kto.application.exception.KtoProviderException;
import koready_backend.kto.domain.KtoPlaceDetail;
import koready_backend.kto.domain.KtoPlaceImage;
import koready_backend.kto.domain.KtoPlaceItem;
import tools.jackson.databind.json.JsonMapper;

class KtoCuratedPlaceResponseParserTest {

	private final KtoCuratedPlaceResponseParser parser =
		new KtoCuratedPlaceResponseParser(JsonMapper.builder().build());

	@Test
	void parsesAKeywordSearchItemAndNormalizesBlankFields() throws IOException {
		List<KtoPlaceItem> items = parser.parseSearch(fixture("curated-search.json"));

		assertEquals(1, items.size());
		assertEquals("126508", items.getFirst().contentId());
		assertEquals("경복궁", items.getFirst().title());
		assertEquals("12", items.getFirst().contentTypeId());
		assertNull(items.getFirst().address2());
	}

	@Test
	void parsesTheDetailOverviewAndHomepage() throws IOException {
		KtoPlaceDetail detail = parser.parseDetail(fixture("curated-detail.json"));

		assertEquals("126508", detail.place().contentId());
		assertEquals("경복궁 소개", detail.overview());
		assertEquals("https://www.royalpalace.go.kr", detail.homepage());
	}

	@Test
	void parsesDetailImagesInProviderOrderAndIgnoresBlankImageUrls() throws IOException {
		List<KtoPlaceImage> images = parser.parseImages(fixture("curated-detail-images.json"));

		assertEquals(2, images.size());
		assertEquals("https://example.invalid/original-1.jpg", images.getFirst().originImageUrl());
		assertEquals("https://example.invalid/thumb-1.jpg", images.getFirst().thumbnailImageUrl());
		assertEquals(1, images.getFirst().sourceOrder());
		assertEquals(3, images.get(1).sourceOrder());
	}

	@Test
	void acceptsTheProvidersEmptyStringItemsShape() {
		byte[] payload = """
			{"response":{"header":{"resultCode":"0000"},"body":{"items":"","totalCount":0}}}
			""".getBytes(StandardCharsets.UTF_8);

		assertEquals(List.of(), parser.parseSearch(payload));
	}

	@Test
	void rejectsAProviderErrorBeforeReadingItems() throws IOException {
		String payload = new String(fixture("curated-search.json"), StandardCharsets.UTF_8)
			.replace("\"0000\"", "\"22\"");

		assertThrows(KtoProviderException.class,
			() -> parser.parseSearch(payload.getBytes(StandardCharsets.UTF_8)));
	}

	private byte[] fixture(String name) throws IOException {
		try (var input = getClass().getResourceAsStream("/fixtures/kto/" + name)) {
			if (input == null) {
				throw new IOException("Fixture not found: " + name);
			}
			return input.readAllBytes();
		}
	}
}
