package koready_backend.kto.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import koready_backend.kto.application.exception.KtoProviderException;
import koready_backend.kto.application.exception.KtoResponseParseException;
import koready_backend.kto.domain.KtoPlaceItem;
import koready_backend.kto.domain.KtoSyncPage;
import tools.jackson.databind.json.JsonMapper;

class KtoAreaBasedSyncResponseParserTest {

	private final KtoAreaBasedSyncResponseParser parser =
		new KtoAreaBasedSyncResponseParser(JsonMapper.builder().build());

	@Test
	void parsesAProfiledAreaBasedSyncPageWithoutRetainingUnknownFields() throws IOException {
		byte[] payload = fixture("/fixtures/kto/area-based-sync-page.json");

		KtoSyncPage page = parser.parse(payload);

		assertEquals(3, page.pageNumber());
		assertEquals(200, page.pageSize());
		assertEquals(68_493, page.totalCount());
		assertEquals(2, page.items().size());
		assertEquals(payload.length, page.responseBytes());
		assertTrue(page.responseSha256().matches("[0-9a-f]{64}"));

		KtoPlaceItem first = page.items().getFirst();
		assertEquals("100001", first.contentId());
		assertEquals("12", first.contentTypeId());
		assertEquals("테스트 관광지", first.title());
		assertEquals("서울특별시 테스트구", first.address1());
		assertEquals("테스트로 1", first.address2());
		assertEquals("1", first.areaCode());
		assertEquals("1", first.districtCode());
		assertEquals("A01", first.categoryCode1());
		assertEquals("A0101", first.categoryCode2());
		assertEquals("A01010100", first.categoryCode3());
		assertEquals("Type3", first.copyrightType());
		assertEquals("20260101090000", first.createdTime());
		assertEquals("https://example.invalid/image.jpg", first.primaryImageUrl());
		assertEquals("https://example.invalid/thumbnail.jpg", first.thumbnailImageUrl());
		assertEquals("126.9780000000", first.longitude());
		assertEquals("37.5665000000", first.latitude());
		assertEquals("6", first.mapLevel());
		assertEquals("20260701090000", first.modifiedTime());
		assertNull(first.phoneNumber());
		assertEquals("00000", first.postalCode());
		assertTrue(first.visible());
		assertEquals("11", first.legalDongRegionCode());
		assertEquals("110", first.legalDongDistrictCode());
		assertEquals("VE", first.classificationCode1());
		assertEquals("VE01", first.classificationCode2());
		assertEquals("VE010100", first.classificationCode3());
		assertTrue(first.sourceHash().matches("[0-9a-f]{64}"));

		KtoPlaceItem hidden = page.items().get(1);
		assertNull(hidden.areaCode());
		assertNull(hidden.primaryImageUrl());
		assertFalse(hidden.visible());
	}

	@Test
	void treatsAnEmptyItemsStringAsAnEmptyPage() {
		byte[] payload = successResponse("\"\"").getBytes(StandardCharsets.UTF_8);

		KtoSyncPage page = parser.parse(payload);

		assertTrue(page.items().isEmpty());
		assertEquals(0, page.totalCount());
	}

	@Test
	void acceptsOneItemReturnedAsAnObjectInsteadOfAnArray() {
		String item = "{\"contentid\":\"100003\",\"title\":\"단일 관광지\",\"showflag\":\"1\"}";
		byte[] payload = successResponse("{\"item\":" + item + "}").getBytes(StandardCharsets.UTF_8);

		KtoSyncPage page = parser.parse(payload);

		assertEquals(1, page.items().size());
		assertEquals("100003", page.items().getFirst().contentId());
	}

	@Test
	void includesUnknownSourceFieldsInTheItemHash() {
		String first = "{\"contentid\":\"100004\",\"title\":\"해시 장소\",\"futureField\":\"A\"}";
		String second = "{\"contentid\":\"100004\",\"title\":\"해시 장소\",\"futureField\":\"B\"}";

		String firstHash = parser.parse(successResponse("{\"item\":" + first + "}")
			.getBytes(StandardCharsets.UTF_8)).items().getFirst().sourceHash();
		String secondHash = parser.parse(successResponse("{\"item\":" + second + "}")
			.getBytes(StandardCharsets.UTF_8)).items().getFirst().sourceHash();

		assertNotEquals(firstHash, secondHash);
	}

	@Test
	void rejectsAProviderErrorWithoutEchoingTheProviderMessage() {
		String payload = """
			{"response":{"header":{"resultCode":"30","resultMsg":"SERVICE KEY IS NOT REGISTERED"}}}
			""";

		KtoProviderException exception = assertThrows(
			KtoProviderException.class,
			() -> parser.parse(payload.getBytes(StandardCharsets.UTF_8)));

		assertEquals("30", exception.providerCode());
		assertFalse(exception.getMessage().contains("SERVICE KEY"));
	}

	@Test
	void rejectsMalformedJsonWithoutIncludingThePayload() {
		String payload = "{not-json-secret-content";

		KtoResponseParseException exception = assertThrows(
			KtoResponseParseException.class,
			() -> parser.parse(payload.getBytes(StandardCharsets.UTF_8)));

		assertFalse(exception.getMessage().contains("secret-content"));
	}

	private byte[] fixture(String path) throws IOException {
		try (var input = getClass().getResourceAsStream(path)) {
			if (input == null) {
				throw new IOException("Fixture not found: " + path);
			}
			return input.readAllBytes();
		}
	}

	private String successResponse(String items) {
		return """
			{"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{
			"items":%s,"numOfRows":200,"pageNo":1,"totalCount":0}}}
			""".formatted(items);
	}
}
