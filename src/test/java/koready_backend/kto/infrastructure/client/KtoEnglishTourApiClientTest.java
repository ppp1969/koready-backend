package koready_backend.kto.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import koready_backend.kto.infrastructure.config.KtoBatchProperties;
import koready_backend.kto.infrastructure.config.KtoEnglishApiProperties;
import tools.jackson.databind.json.JsonMapper;

class KtoEnglishTourApiClientTest {

	private static final String TEST_KEY = "test-service-key";

	@Test
	void requestsAndParsesTheEnglishSyncEndpointWithoutExposingTheKey() {
		RestClient.Builder builder = RestClient.builder().baseUrl(properties().baseUrl());
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KtoEnglishTourApiClient client = client(builder.build());
		server.expect(request -> {
			assertEquals("/B551011/EngService2/areaBasedSyncList2", request.getURI().getPath());
			var query = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
			assertEquals("200", query.getFirst("numOfRows"));
			assertEquals("7", query.getFirst("pageNo"));
			assertEquals(TEST_KEY, query.getFirst("serviceKey"));
		}).andRespond(withSuccess(successPage(7, 200), MediaType.APPLICATION_JSON));

		var page = client.fetchFetchedPage(7).page();

		assertEquals(7, page.pageNumber());
		assertEquals(25_348, page.totalCount());
		assertEquals("eng-100", page.items().getFirst().contentId());
		assertEquals("old-eng-100", page.items().getFirst().oldContentId());
		assertEquals("76", page.items().getFirst().contentTypeId());
		server.verify();
	}

	@Test
	void preservesTheRequestedPageSizeWhenTheEnglishLastPageShrinks() {
		RestClient.Builder builder = RestClient.builder().baseUrl(properties().baseUrl());
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KtoEnglishTourApiClient client = client(builder.build());
		server.expect(request -> { })
			.andRespond(withSuccess(successPage(127, 148), MediaType.APPLICATION_JSON));

		var page = client.fetchFetchedPage(127).page();

		assertEquals(200, page.pageSize());
		assertEquals(127, page.pageNumber());
		server.verify();
	}

	@Test
	void retriesTheEnglishProviderLimitCode() {
		RestClient.Builder builder = RestClient.builder().baseUrl(properties().baseUrl());
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KtoEnglishTourApiClient client = client(builder.build());
		server.expect(request -> { }).andRespond(withSuccess(
			"{\"response\":{\"header\":{\"resultCode\":\"22\",\"resultMsg\":\"limited\"}}}",
			MediaType.APPLICATION_JSON));
		server.expect(request -> { }).andRespond(withSuccess(successPage(1, 200), MediaType.APPLICATION_JSON));

		assertEquals(1, client.fetchFetchedPage(1).page().pageNumber());
		server.verify();
	}

	private KtoEnglishTourApiClient client(RestClient restClient) {
		return new KtoEnglishTourApiClient(
			restClient,
			properties(),
			new KtoBatchProperties(200, 50, 1),
			new KtoEnglishAreaBasedSyncResponseParser(JsonMapper.builder().build()),
			Clock.systemUTC(),
			delay -> { });
	}

	private KtoEnglishApiProperties properties() {
		return new KtoEnglishApiProperties(
			URI.create("https://apis.data.go.kr/B551011/EngService2"),
			TEST_KEY,
			4 * 1024 * 1024,
			Duration.ofSeconds(3),
			Duration.ofSeconds(10),
			"ETC",
			"KoReady");
	}

	private String successPage(int pageNumber, int responsePageSize) {
		return """
			{"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{
			  "items":{"item":[{
			    "contentid":"eng-100",
			    "oldContentId":"old-eng-100",
			    "contentTypeId":"76",
			    "title":"Sample place",
			    "addr1":"Seoul",
			    "firstImage":"https://example.invalid/sample.jpg",
			    "mapX":"126.978",
			    "mapY":"37.5665",
			    "modifiedTime":"20260701090000",
			    "showFlag":"1"
			  }]},
			  "numOfRows":%d,
			  "pageNo":%d,
			  "totalCount":25348
			}}}
			""".formatted(responsePageSize, pageNumber);
	}
}
