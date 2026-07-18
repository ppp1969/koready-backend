package koready_backend.kto.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import koready_backend.kto.application.exception.KtoResponseParseException;
import koready_backend.kto.domain.KtoFestivalPage;
import tools.jackson.databind.json.JsonMapper;

class KtoFestivalResponseParserTest {

	private final KtoFestivalResponseParser parser =
		new KtoFestivalResponseParser(JsonMapper.builder().build());

	@Test
	void parsesFestivalDatesAndNormalizesBlankFields() throws IOException {
		byte[] payload = fixture();

		KtoFestivalPage page = parser.parse(payload);

		assertEquals(3, page.pageNumber());
		assertEquals(200, page.pageSize());
		assertEquals(187, page.totalCount());
		assertEquals(payload.length, page.responseBytes());
		assertEquals(2, page.items().size());
		assertEquals("700001", page.items().getFirst().place().contentId());
		assertEquals(LocalDate.of(2026, 10, 16), page.items().getFirst().startDate());
		assertEquals(LocalDate.of(2026, 10, 18), page.items().getFirst().endDate());
		assertEquals(LocalDate.of(2026, 4, 16), page.items().getFirst().visibleFrom());
		assertNull(page.items().get(1).place().primaryImageUrl());
		assertNull(page.items().get(1).progressType());
	}

	@Test
	void rejectsAnInvalidCalendarDate() throws IOException {
		String payload = new String(fixture())
			.replace("20261018", "20260230");

		assertThrows(KtoResponseParseException.class, () -> parser.parse(payload.getBytes()));
	}

	@Test
	void rejectsAMissingRequiredEventDate() throws IOException {
		String payload = new String(fixture())
			.replace("\"eventstartdate\": \"20261016\",", "");

		assertThrows(KtoResponseParseException.class, () -> parser.parse(payload.getBytes()));
	}

	private byte[] fixture() throws IOException {
		try (var input = getClass().getResourceAsStream("/fixtures/kto/festival-page.json")) {
			if (input == null) {
				throw new IOException("Fixture not found");
			}
			return input.readAllBytes();
		}
	}
}
