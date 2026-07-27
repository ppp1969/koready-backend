package koready_backend.kto.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import koready_backend.kto.application.exception.KtoResponseTooLargeException;
import koready_backend.kto.infrastructure.config.KtoBatchProperties;
import koready_backend.kto.infrastructure.config.KtoRelatedTourApiProperties;
import tools.jackson.databind.json.JsonMapper;

class KtoRelatedTourApiClientTest {

	private static final String TEST_KEY = "test-service-key";

	@Test
	void requestsAreaBasedRelationsWithRequiredRegionParameters()
		throws IOException {
		byte[] payload = fixture();
		KtoRelatedTourApiProperties properties =
			properties(4 * 1024 * 1024);
		RestClient.Builder builder =
			RestClient.builder().baseUrl(properties.baseUrl());
		MockRestServiceServer server =
			MockRestServiceServer.bindTo(builder).build();
		KtoRelatedTourApiClient client =
			client(builder.build(), properties);

		server.expect(request -> {
			assertEquals(HttpMethod.GET, request.getMethod());
			assertEquals(
				"/B551011/TarRlteTarService1/areaBasedList1",
				request.getURI().getPath());
			var query = UriComponentsBuilder.fromUri(request.getURI())
				.build().getQueryParams();
			assertEquals("200", query.getFirst("numOfRows"));
			assertEquals("1", query.getFirst("pageNo"));
			assertEquals("202606", query.getFirst("baseYm"));
			assertEquals("11", query.getFirst("areaCd"));
			assertEquals("11530", query.getFirst("signguCd"));
			assertEquals(TEST_KEY, query.getFirst("serviceKey"));
		}).andRespond(withSuccess(payload, MediaType.APPLICATION_JSON));

		var fetched = client.fetchPage("202606", "11", "11530", 1);

		assertEquals(2, fetched.page().totalCount());
		assertEquals(payload.length, fetched.rawPayload().length);
		assertEquals(200, fetched.call().httpStatus());
		server.verify();
	}

	@Test
	void rejectsAnOversizedResponseBeforeParsing() {
		KtoRelatedTourApiProperties properties = properties(64);
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server =
			MockRestServiceServer.bindTo(builder).build();
		KtoRelatedTourApiClient client =
			client(builder.build(), properties);
		server.expect(request -> { })
			.andRespond(withSuccess(
				"x".repeat(65), MediaType.APPLICATION_JSON));

		assertThrows(
			KtoResponseTooLargeException.class,
			() -> client.fetchPage("202606", "11", "11530", 1));

		server.verify();
	}

	@Test
	void normalizesAProviderEmptyPageToTheRequestedPageSize() {
		byte[] payload = """
			{
			  "response": {
			    "header": {
			      "resultCode": "0000",
			      "resultMsg": "OK"
			    },
			    "body": {
			      "items": "",
			      "numOfRows": 0,
			      "pageNo": 1,
			      "totalCount": 0
			    }
			  }
			}
			""".getBytes(StandardCharsets.UTF_8);
		KtoRelatedTourApiProperties properties =
			properties(4 * 1024 * 1024);
		RestClient.Builder builder =
			RestClient.builder().baseUrl(properties.baseUrl());
		MockRestServiceServer server =
			MockRestServiceServer.bindTo(builder).build();
		KtoRelatedTourApiClient client =
			client(builder.build(), properties);
		server.expect(request -> { })
			.andRespond(withSuccess(payload, MediaType.APPLICATION_JSON));

		var fetched = client.fetchPage(
			"202606", "41", "41110", 1);

		assertEquals(0, fetched.page().totalCount());
		assertEquals(0, fetched.page().items().size());
		assertEquals(200, fetched.page().pageSize());
		server.verify();
	}

	@Test
	void retriesAProviderRateLimitBeforeReturningThePage()
		throws IOException {
		byte[] payload = fixture();
		KtoRelatedTourApiProperties properties =
			properties(4 * 1024 * 1024);
		RestClient.Builder builder =
			RestClient.builder().baseUrl(properties.baseUrl());
		MockRestServiceServer server =
			MockRestServiceServer.bindTo(builder).build();
		KtoRelatedTourApiClient client =
			new KtoRelatedTourApiClient(
				builder.build(),
				properties,
				new KtoBatchProperties(200, 50, 1),
				new KtoRelatedTourResponseParser(
					JsonMapper.builder().build()),
				Clock.systemUTC(),
				delayMillis -> { });
		server.expect(request -> { })
			.andRespond(
				org.springframework.test.web.client.response
					.MockRestResponseCreators
					.withStatus(HttpStatus.TOO_MANY_REQUESTS));
		server.expect(request -> { })
			.andRespond(withSuccess(
				payload, MediaType.APPLICATION_JSON));

		var fetched = client.fetchPage(
			"202606", "11", "11530", 1);

		assertEquals(2, fetched.page().totalCount());
		server.verify();
	}

	private KtoRelatedTourApiClient client(
		RestClient restClient,
		KtoRelatedTourApiProperties properties
	) {
		return new KtoRelatedTourApiClient(
			restClient,
			properties,
			new KtoBatchProperties(200, 50, 1),
			new KtoRelatedTourResponseParser(
				JsonMapper.builder().build()));
	}

	private KtoRelatedTourApiProperties properties(
		int maxResponseBytes
	) {
		return new KtoRelatedTourApiProperties(
			URI.create(
				"https://apis.data.go.kr/B551011/TarRlteTarService1"),
			TEST_KEY,
			maxResponseBytes,
			Duration.ofSeconds(3),
			Duration.ofSeconds(10),
			"ETC",
			"KoReady");
	}

	private byte[] fixture() throws IOException {
		try (var input = getClass().getResourceAsStream(
			"/fixtures/kto/related-tour-page.json")) {
			if (input == null) {
				throw new IOException("Fixture not found");
			}
			return input.readAllBytes();
		}
	}
}
