package koready_backend.kto.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

import koready_backend.kto.application.exception.KtoResponseTooLargeException;
import koready_backend.kto.infrastructure.config.KtoBatchProperties;
import koready_backend.kto.infrastructure.config.KtoPhotoGalleryApiProperties;
import tools.jackson.databind.json.JsonMapper;

class KtoPhotoGalleryApiClientTest {

	private static final String TEST_KEY = "test-service-key";

	@Test
	void requestsGalleryListWithBoundedPagination() throws IOException {
		byte[] payload = fixture();
		KtoPhotoGalleryApiProperties properties =
			properties(4 * 1024 * 1024);
		RestClient.Builder builder =
			RestClient.builder().baseUrl(properties.baseUrl());
		MockRestServiceServer server =
			MockRestServiceServer.bindTo(builder).build();
		KtoPhotoGalleryApiClient client =
			client(builder.build(), properties);

		server.expect(request -> {
			assertEquals(HttpMethod.GET, request.getMethod());
			assertEquals(
				"/B551011/PhotoGalleryService1/galleryList1",
				request.getURI().getPath());
			var query = UriComponentsBuilder.fromUri(request.getURI())
				.build().getQueryParams();
			assertEquals("200", query.getFirst("numOfRows"));
			assertEquals("1", query.getFirst("pageNo"));
			assertEquals("json", query.getFirst("_type"));
			assertEquals(TEST_KEY, query.getFirst("serviceKey"));
		}).andRespond(withSuccess(payload, MediaType.APPLICATION_JSON));

		var fetched = client.fetchPage(1);

		assertEquals(312, fetched.page().totalCount());
		assertEquals(payload.length, fetched.rawPayload().length);
		assertEquals(200, fetched.call().httpStatus());
		server.verify();
	}

	@Test
	void rejectsAnOversizedGalleryResponseBeforeParsing() {
		KtoPhotoGalleryApiProperties properties = properties(64);
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server =
			MockRestServiceServer.bindTo(builder).build();
		KtoPhotoGalleryApiClient client =
			client(builder.build(), properties);
		server.expect(request -> { })
			.andRespond(withSuccess(
				"x".repeat(65), MediaType.APPLICATION_JSON));

		assertThrows(
			KtoResponseTooLargeException.class,
			() -> client.fetchPage(1));

		server.verify();
	}

	private KtoPhotoGalleryApiClient client(
		RestClient restClient,
		KtoPhotoGalleryApiProperties properties
	) {
		return new KtoPhotoGalleryApiClient(
			restClient,
			properties,
			new KtoBatchProperties(200, 50, 1),
			new KtoPhotoGalleryResponseParser(
				JsonMapper.builder().build()));
	}

	private KtoPhotoGalleryApiProperties properties(
		int maxResponseBytes
	) {
		return new KtoPhotoGalleryApiProperties(
			URI.create(
				"https://apis.data.go.kr/B551011/PhotoGalleryService1"),
			TEST_KEY,
			maxResponseBytes,
			Duration.ofSeconds(3),
			Duration.ofSeconds(10),
			"ETC",
			"KoReady");
	}

	private byte[] fixture() throws IOException {
		try (var input = getClass().getResourceAsStream(
			"/fixtures/kto/photo-gallery-page.json")) {
			if (input == null) {
				throw new IOException("Fixture not found");
			}
			return input.readAllBytes();
		}
	}
}
