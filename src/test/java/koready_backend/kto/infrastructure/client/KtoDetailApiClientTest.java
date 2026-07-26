package koready_backend.kto.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import koready_backend.kto.application.exception.KtoResponseTooLargeException;
import koready_backend.kto.domain.KtoDetailOperation;
import koready_backend.kto.domain.KtoDetailTarget;
import koready_backend.kto.infrastructure.config.KtoApiProperties;
import tools.jackson.databind.json.JsonMapper;

class KtoDetailApiClientTest {

	private static final String SUCCESS = """
		{"response":{"header":{"resultCode":"0000"},"body":{
		  "items":{"item":[{"contentid":"100","contenttypeid":"12"}]},
		  "totalCount":1
		}}}
		""";

	@Test
	void sendsContentTypeOnlyToOperationsThatRequireIt() {
		KtoApiProperties properties = properties(4 * 1024 * 1024);
		RestClient.Builder builder = RestClient.builder().baseUrl(properties.baseUrl());
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KtoDetailApiClient client = client(builder.build(), properties);
		server.expect(request -> {
			assertEquals(
				"/B551011/KorService2/detailIntro2",
				request.getURI().getPath());
			var query = UriComponentsBuilder.fromUri(request.getURI())
				.build().getQueryParams();
			assertEquals("100", query.getFirst("contentId"));
			assertEquals("12", query.getFirst("contentTypeId"));
			assertEquals("test-service-key", query.getFirst("serviceKey"));
		}).andRespond(withSuccess(SUCCESS, MediaType.APPLICATION_JSON));
		server.expect(request -> {
			var query = UriComponentsBuilder.fromUri(request.getURI())
				.build().getQueryParams();
			assertEquals(
				"/B551011/KorService2/detailImage2",
				request.getURI().getPath());
			assertNull(query.getFirst("contentTypeId"));
			assertEquals("100", query.getFirst("numOfRows"));
			assertEquals("1", query.getFirst("pageNo"));
		}).andRespond(withSuccess(SUCCESS, MediaType.APPLICATION_JSON));

		KtoDetailTarget target = new KtoDetailTarget(41L, "100", "12");
		client.fetch(KtoDetailOperation.INTRO, target);
		client.fetch(KtoDetailOperation.IMAGE, target);

		server.verify();
	}

	@Test
	void rejectsAnOversizedDetailResponseBeforeParsing() {
		KtoApiProperties properties = properties(64);
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KtoDetailApiClient client = client(builder.build(), properties);
		server.expect(request -> { })
			.andRespond(withSuccess("x".repeat(65), MediaType.APPLICATION_JSON));

		assertThrows(
			KtoResponseTooLargeException.class,
			() -> client.fetch(
				KtoDetailOperation.COMMON,
				new KtoDetailTarget(41L, "100", "12")));
		server.verify();
	}

	private KtoDetailApiClient client(
		RestClient restClient,
		KtoApiProperties properties
	) {
		return new KtoDetailApiClient(
			restClient,
			properties,
			new KtoDetailResponseParser(JsonMapper.builder().build()),
			Clock.fixed(
				Instant.parse("2026-07-27T00:00:00Z"),
				ZoneOffset.UTC),
			delay -> { });
	}

	private KtoApiProperties properties(int maxResponseBytes) {
		return new KtoApiProperties(
			URI.create("https://apis.data.go.kr/B551011/KorService2"),
			"test-service-key",
			maxResponseBytes,
			Duration.ofSeconds(3),
			Duration.ofSeconds(10),
			"ETC",
			"KoReady");
	}
}
