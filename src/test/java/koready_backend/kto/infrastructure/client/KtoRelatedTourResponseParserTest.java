package koready_backend.kto.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import koready_backend.kto.application.exception.KtoResponseParseException;
import koready_backend.kto.domain.KtoRelatedTourPage;
import tools.jackson.databind.json.JsonMapper;

class KtoRelatedTourResponseParserTest {

	private final KtoRelatedTourResponseParser parser =
		new KtoRelatedTourResponseParser(JsonMapper.builder().build());

	@Test
	void parsesRelatedTourFieldsAndRank() throws IOException {
		KtoRelatedTourPage page =
			parser.parse(fixture("related-tour-page.json"));

		assertEquals(1, page.pageNumber());
		assertEquals(2, page.totalCount());
		assertEquals(2, page.items().size());
		assertEquals("202606", page.items().getFirst().baseYearMonth());
		assertEquals(
			"11111111111111111111111111111111",
			page.items().getFirst().sourceTourCode());
		assertEquals("가상 전통시장", page.items().getFirst().sourceName());
		assertEquals("가상 문화공간", page.items().getFirst().relatedName());
		assertEquals("전시시설", page.items().getFirst().categorySmall());
		assertEquals(1, page.items().getFirst().rank());
	}

	@Test
	void rejectsMissingOpaqueIdentifiers() throws IOException {
		byte[] invalid = new String(
			fixture("related-tour-page.json"),
			java.nio.charset.StandardCharsets.UTF_8)
			.replace(
				"\"tAtsCd\": \"11111111111111111111111111111111\"",
				"\"tAtsCd\": \"\"")
			.getBytes(java.nio.charset.StandardCharsets.UTF_8);

		assertThrows(KtoResponseParseException.class,
			() -> parser.parse(invalid));
	}

	private byte[] fixture(String name) throws IOException {
		try (var input = getClass().getResourceAsStream(
			"/fixtures/kto/" + name)) {
			if (input == null) {
				throw new IllegalStateException("Fixture is missing");
			}
			return input.readAllBytes();
		}
	}
}
