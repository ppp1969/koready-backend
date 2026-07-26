package koready_backend.kto.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import koready_backend.kto.application.exception.KtoProviderException;
import koready_backend.kto.domain.KtoDetailOperation;
import tools.jackson.databind.json.JsonMapper;

class KtoDetailResponseParserTest {

	private final KtoDetailResponseParser parser =
		new KtoDetailResponseParser(JsonMapper.builder().build());

	@Test
	void parsesContentTypeSpecificIntroFieldsWithoutHardCodingTheirShape() {
		byte[] payload = response("""
			{"contentid":"100","contenttypeid":"12","usetime":"09:00-18:00",
			 "restdate":"Monday","parking":""}
			""");

		var parsed = parser.parse(KtoDetailOperation.INTRO, payload);

		assertEquals(1, parsed.items().size());
		assertEquals("09:00-18:00", parsed.items().getFirst().get("usetime"));
		assertEquals("Monday", parsed.items().getFirst().get("restdate"));
		assertEquals(false, parsed.items().getFirst().containsKey("parking"));
		assertEquals(payload.length, parsed.responseBytes());
	}

	@Test
	void parsesRepeatedInformationAndImageItems() {
		byte[] info = response("""
			[
			  {"contentid":"100","serialnum":"1","infoname":"Admission","infotext":"Free"},
			  {"contentid":"100","serialnum":"2","infoname":"Guide","infotext":"Reservation"}
			]
			""");
		byte[] image = response("""
			[
			  {"contentid":"100","serialnum":"1","originimgurl":"https://example.invalid/1.jpg"},
			  {"contentid":"100","serialnum":"2","originimgurl":"https://example.invalid/2.jpg"}
			]
			""");

		assertEquals(2, parser.parse(KtoDetailOperation.INFO, info).items().size());
		assertEquals(
			"https://example.invalid/2.jpg",
			parser.parse(KtoDetailOperation.IMAGE, image)
				.items().get(1).get("originimgurl"));
	}

	@Test
	void acceptsTheProviderEmptyItemsShape() {
		byte[] payload = """
			{"response":{"header":{"resultCode":"0000"},"body":{"items":"","totalCount":0}}}
			""".getBytes(StandardCharsets.UTF_8);

		assertEquals(
			List.of(),
			parser.parse(KtoDetailOperation.INFO, payload).items());
	}

	@Test
	void rejectsAProviderError() {
		byte[] payload = """
			{"response":{"header":{"resultCode":"22"},"body":{"items":""}}}
			""".getBytes(StandardCharsets.UTF_8);

		assertThrows(
			KtoProviderException.class,
			() -> parser.parse(KtoDetailOperation.COMMON, payload));
	}

	private byte[] response(String itemJson) {
		String items = itemJson.stripLeading().startsWith("[")
			? itemJson
			: "[" + itemJson + "]";
		return ("""
			{"response":{"header":{"resultCode":"0000"},"body":{
			  "items":{"item":%s},"totalCount":1
			}}}
			""".formatted(items)).getBytes(StandardCharsets.UTF_8);
	}
}
