package koready_backend.kto.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import koready_backend.kto.application.exception.KtoResponseTooLargeException;
import koready_backend.kto.domain.KtoPlaceDetail;
import koready_backend.kto.infrastructure.config.KtoApiProperties;
import tools.jackson.databind.json.JsonMapper;

class KtoCuratedPlaceApiClientTest {

	private static final String TEST_KEY = "test-service-key";

	@Test
	void requestsOneBoundedKeywordPage() throws IOException {
		KtoApiProperties properties = properties(4 * 1024 * 1024);
		RestClient.Builder builder = RestClient.builder().baseUrl(properties.baseUrl());
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KtoCuratedPlaceApiClient client = client(builder.build(), properties);
		server.expect(request -> {
			assertEquals(HttpMethod.GET, request.getMethod());
			assertEquals("/B551011/KorService2/searchKeyword2", request.getURI().getPath());
			var query = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
			assertEquals("10", query.getFirst("numOfRows"));
			assertEquals("1", query.getFirst("pageNo"));
			assertEquals(
				"경복궁",
				URLDecoder.decode(query.getFirst("keyword"), StandardCharsets.UTF_8));
			assertEquals(TEST_KEY, query.getFirst("serviceKey"));
		}).andRespond(withSuccess(fixture("curated-search.json"), MediaType.APPLICATION_JSON));

		assertEquals("126508", client.search("경복궁").getFirst().contentId());
		server.verify();
	}

	@Test
	void requestsThePinnedDetailWithoutUnsupportedFlags() throws IOException {
		KtoApiProperties properties = properties(4 * 1024 * 1024);
		RestClient.Builder builder = RestClient.builder().baseUrl(properties.baseUrl());
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KtoCuratedPlaceApiClient client = client(builder.build(), properties);
		server.expect(request -> {
			assertEquals("/B551011/KorService2/detailCommon2", request.getURI().getPath());
			var query = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
			assertEquals("126508", query.getFirst("contentId"));
			assertEquals(null, query.getFirst("defaultYN"));
			assertEquals(TEST_KEY, query.getFirst("serviceKey"));
		}).andRespond(withSuccess(fixture("curated-detail.json"), MediaType.APPLICATION_JSON));

		KtoPlaceDetail detail = client.fetchDetail("126508");

		assertEquals("경복궁 소개", detail.overview());
		server.verify();
	}

	@Test
	void stopsBeforeParsingWhenTheCuratedResponseIsTooLarge() {
		KtoApiProperties properties = properties(64);
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KtoCuratedPlaceApiClient client = client(builder.build(), properties);
		server.expect(request -> { }).andRespond(withSuccess("x".repeat(65), MediaType.APPLICATION_JSON));

		assertThrows(KtoResponseTooLargeException.class, () -> client.search("경복궁"));
		server.verify();
	}

	private KtoCuratedPlaceApiClient client(RestClient restClient, KtoApiProperties properties) {
		return new KtoCuratedPlaceApiClient(
			restClient,
			properties,
			new KtoCuratedPlaceResponseParser(JsonMapper.builder().build()));
	}

	private KtoApiProperties properties(int maxResponseBytes) {
		return new KtoApiProperties(
			URI.create("https://apis.data.go.kr/B551011/KorService2"),
			TEST_KEY,
			maxResponseBytes,
			Duration.ofSeconds(3),
			Duration.ofSeconds(10),
			"ETC",
			"KoReady");
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
