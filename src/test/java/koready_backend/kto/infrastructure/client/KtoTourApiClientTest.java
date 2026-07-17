package koready_backend.kto.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import koready_backend.kto.application.exception.KtoClientConfigurationException;
import koready_backend.kto.application.exception.KtoProviderException;
import koready_backend.kto.application.exception.KtoResponseTooLargeException;
import koready_backend.kto.application.exception.KtoTransportException;
import koready_backend.kto.domain.KtoSyncPage;
import koready_backend.kto.infrastructure.config.KtoApiProperties;
import koready_backend.kto.infrastructure.config.KtoBatchProperties;
import tools.jackson.databind.json.JsonMapper;

class KtoTourApiClientTest {

	private static final String TEST_KEY = "test-service-key";

	@Test
	void requestsOneConfiguredPageWithTheRequiredTourApiParameters() throws IOException {
		byte[] payload = fixture("/fixtures/kto/area-based-sync-page.json");
		KtoApiProperties properties = properties(TEST_KEY, 4 * 1024 * 1024);
		RestClient.Builder builder = RestClient.builder().baseUrl(properties.baseUrl());
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KtoTourApiClient client = client(builder.build(), properties);

		server.expect(request -> {
			assertEquals(HttpMethod.GET, request.getMethod());
			assertEquals("/B551011/KorService2/areaBasedSyncList2", request.getURI().getPath());
			var query = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
			assertEquals("200", query.getFirst("numOfRows"));
			assertEquals("3", query.getFirst("pageNo"));
			assertEquals("ETC", query.getFirst("MobileOS"));
			assertEquals("KoReady", query.getFirst("MobileApp"));
			assertEquals("json", query.getFirst("_type"));
			assertEquals(TEST_KEY, query.getFirst("serviceKey"));
		}).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(payload, MediaType.APPLICATION_JSON));

		KtoSyncPage page = client.fetchPage(3);

		assertEquals(3, page.pageNumber());
		server.verify();
	}

	@Test
	void stopsBeforeParsingWhenTheResponseExceedsTheConfiguredLimit() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KtoTourApiClient client = client(builder.build(), properties(TEST_KEY, 64));
		server.expect(request -> { }).andRespond(withSuccess("x".repeat(65), MediaType.APPLICATION_JSON));

		KtoResponseTooLargeException exception = assertThrows(
			KtoResponseTooLargeException.class,
			() -> client.fetchPage(1));

		assertEquals(64, exception.maxResponseBytes());
		server.verify();
	}

	@Test
	void rejectsAMissingServiceKeyBeforeSendingARequest() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KtoTourApiClient client = client(builder.build(), properties("  ", 1024));

		assertThrows(KtoClientConfigurationException.class, () -> client.fetchPage(1));
		server.verify();
	}

	@Test
	void rejectsAnInvalidPageNumberBeforeSendingARequest() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KtoTourApiClient client = client(builder.build(), properties(TEST_KEY, 1024));

		assertThrows(IllegalArgumentException.class, () -> client.fetchPage(0));
		server.verify();
	}

	@Test
	void hidesTheServiceKeyWhenTheProviderReturnsAnHttpError() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KtoTourApiClient client = client(builder.build(), properties(TEST_KEY, 1024));
		server.expect(request -> { }).andRespond(withServerError());

		KtoProviderException exception = assertThrows(KtoProviderException.class, () -> client.fetchPage(1));

		assertFalse(exception.getMessage().contains(TEST_KEY));
		server.verify();
	}

	@Test
	void discardsTransportErrorsThatCouldContainTheRequestUri() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KtoTourApiClient client = client(builder.build(), properties(TEST_KEY, 1024));
		server.expect(request -> { })
			.andRespond(withException(new IOException("failed uri contained " + TEST_KEY)));

		KtoTransportException exception = assertThrows(KtoTransportException.class, () -> client.fetchPage(1));

		assertFalse(exception.getMessage().contains(TEST_KEY));
		assertNull(exception.getCause());
		server.verify();
	}

	private KtoTourApiClient client(RestClient restClient, KtoApiProperties properties) {
		return new KtoTourApiClient(
			restClient,
			properties,
			new KtoBatchProperties(200, 50, 1),
			new KtoAreaBasedSyncResponseParser(JsonMapper.builder().build()));
	}

	private KtoApiProperties properties(String serviceKey, int maxResponseBytes) {
		return new KtoApiProperties(
			URI.create("https://apis.data.go.kr/B551011/KorService2"),
			serviceKey,
			maxResponseBytes,
			Duration.ofSeconds(3),
			Duration.ofSeconds(10),
			"ETC",
			"KoReady");
	}

	private byte[] fixture(String path) throws IOException {
		try (var input = getClass().getResourceAsStream(path)) {
			if (input == null) {
				throw new IOException("Fixture not found: " + path);
			}
			return input.readAllBytes();
		}
	}
}
