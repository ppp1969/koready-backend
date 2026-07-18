package koready_backend.kto.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import koready_backend.kto.application.exception.KtoResponseTooLargeException;
import koready_backend.kto.application.model.KtoFetchedFestivalPage;
import koready_backend.kto.infrastructure.config.KtoApiProperties;
import koready_backend.kto.infrastructure.config.KtoBatchProperties;
import tools.jackson.databind.json.JsonMapper;

class KtoFestivalApiClientTest {

	private static final String TEST_KEY = "test-service-key";

	@Test
	void requestsOneFestivalPageWithTheEventStartDate() throws IOException {
		byte[] payload = fixture();
		KtoApiProperties properties = properties(4 * 1024 * 1024);
		RestClient.Builder builder = RestClient.builder().baseUrl(properties.baseUrl());
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KtoFestivalApiClient client = client(builder.build(), properties);

		server.expect(request -> {
			assertEquals(HttpMethod.GET, request.getMethod());
			assertEquals("/B551011/KorService2/searchFestival2", request.getURI().getPath());
			var query = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
			assertEquals("200", query.getFirst("numOfRows"));
			assertEquals("3", query.getFirst("pageNo"));
			assertEquals("20260701", query.getFirst("eventStartDate"));
			assertEquals(TEST_KEY, query.getFirst("serviceKey"));
		}).andRespond(withSuccess(payload, MediaType.APPLICATION_JSON));

		KtoFetchedFestivalPage fetched = client.fetchPage(LocalDate.of(2026, 7, 1), 3);

		assertEquals(3, fetched.page().pageNumber());
		assertEquals(payload.length, fetched.rawPayload().length);
		assertEquals(200, fetched.call().httpStatus());
		server.verify();
	}

	@Test
	void stopsBeforeParsingWhenTheFestivalResponseIsTooLarge() {
		KtoApiProperties properties = properties(64);
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KtoFestivalApiClient client = client(builder.build(), properties);
		server.expect(request -> { }).andRespond(withSuccess("x".repeat(65), MediaType.APPLICATION_JSON));

		assertThrows(
			KtoResponseTooLargeException.class,
			() -> client.fetchPage(LocalDate.of(2026, 7, 1), 1));

		server.verify();
	}

	@Test
	void keepsTheRequestedPageSizeWhenTheProviderReportsOnlyTheLastPageItemCount()
		throws IOException {
		byte[] payload = new String(fixture(), StandardCharsets.UTF_8)
			.replace("\"numOfRows\": 200", "\"numOfRows\": 3")
			.getBytes(StandardCharsets.UTF_8);
		KtoApiProperties properties = properties(4 * 1024 * 1024);
		RestClient.Builder builder = RestClient.builder().baseUrl(properties.baseUrl());
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KtoFestivalApiClient client = client(builder.build(), properties);
		server.expect(request -> { }).andRespond(withSuccess(payload, MediaType.APPLICATION_JSON));

		KtoFetchedFestivalPage fetched = client.fetchPage(LocalDate.of(2026, 7, 1), 3);

		assertEquals(200, fetched.page().pageSize());
		server.verify();
	}

	private KtoFestivalApiClient client(RestClient restClient, KtoApiProperties properties) {
		return new KtoFestivalApiClient(
			restClient,
			properties,
			new KtoBatchProperties(200, 50, 1),
			new KtoFestivalResponseParser(JsonMapper.builder().build()));
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

	private byte[] fixture() throws IOException {
		try (var input = getClass().getResourceAsStream("/fixtures/kto/festival-page.json")) {
			if (input == null) {
				throw new IOException("Fixture not found");
			}
			return input.readAllBytes();
		}
	}
}
