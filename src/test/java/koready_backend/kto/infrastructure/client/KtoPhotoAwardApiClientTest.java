package koready_backend.kto.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import koready_backend.kto.infrastructure.config.KtoApiProperties;
import tools.jackson.databind.json.JsonMapper;

class KtoPhotoAwardApiClientTest {
	@Test
	void fetchesTheBoundedPhotoAwardSyncPage() throws IOException {
		RestClient.Builder builder = RestClient.builder()
			.baseUrl("https://apis.data.go.kr/B551011/PhokoAwrdService");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KtoApiProperties properties = new KtoApiProperties(
			URI.create("https://apis.data.go.kr/B551011/KorService2"), "test-key",
			4 * 1024 * 1024, Duration.ofSeconds(3), Duration.ofSeconds(10), "ETC", "KoReady");
		var client = new KtoPhotoAwardApiClient(builder.build(), properties,
			new KtoPhotoAwardResponseParser(JsonMapper.builder().build()));
		server.expect(request -> {
			assertEquals("/B551011/PhokoAwrdService/phokoAwrdSyncList", request.getURI().getPath());
			var query = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
			assertEquals("200", query.getFirst("numOfRows"));
			assertEquals("test-key", query.getFirst("serviceKey"));
		}).andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));

		assertEquals(2, client.fetchAll().size());
		server.verify();
	}

	private byte[] fixture() throws IOException {
		try (var input = getClass().getResourceAsStream("/fixtures/kto/photo-award-page.json")) {
			if (input == null) throw new IOException("Fixture not found");
			return input.readAllBytes();
		}
	}
}
