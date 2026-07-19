package koready_backend.location.infrastructure.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.databind.json.JsonMapper;
import koready_backend.location.application.exception.LocationProviderUnavailableException;
import koready_backend.location.domain.LocationSearchResultType;
import koready_backend.location.infrastructure.config.KakaoLocationProperties;

class KakaoLocationSearchProviderTest {

	private static final String REST_API_KEY = "test-kakao-rest-api-key";

	@Test
	void callsAddressAndKeywordSearchWithHeaderAndNormalizesBothResponses() {
		KakaoLocationProperties properties = properties(REST_API_KEY, 512 * 1024);
		RestClient.Builder builder = RestClient.builder().baseUrl(properties.baseUrl().toString());
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KakaoLocationSearchProvider provider = provider(builder.build(), properties);

		server.expect(request -> {
			assertEquals(HttpMethod.GET, request.getMethod());
			assertEquals("/v2/local/search/address.json", request.getURI().getPath());
			assertEquals("KakaoAK " + REST_API_KEY,
				request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
			var query = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
			assertEquals("성신여자대학교", decode(query.getFirst("query")));
			assertEquals("20", query.getFirst("size"));
		}).andRespond(withSuccess(addressResponse(), MediaType.APPLICATION_JSON));

		server.expect(request -> {
			assertEquals(HttpMethod.GET, request.getMethod());
			assertEquals("/v2/local/search/keyword.json", request.getURI().getPath());
			assertEquals("KakaoAK " + REST_API_KEY,
				request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
			var query = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
			assertEquals("성신여자대학교", decode(query.getFirst("query")));
			assertEquals("15", query.getFirst("size"));
		}).andRespond(withSuccess(keywordResponse(), MediaType.APPLICATION_JSON));

		var results = provider.search("성신여자대학교", 20);

		assertEquals(2, results.size());
		assertEquals(LocationSearchResultType.ADDRESS, results.get(0).resultType());
		assertEquals("성신여자대학교", results.get(0).name());
		assertEquals("서울특별시", results.get(0).sido());
		assertEquals(LocationSearchResultType.PLACE, results.get(1).resultType());
		assertEquals("123456789", results.get(1).providerPlaceId());
		assertEquals("서울", results.get(1).sido());
		assertEquals("성북구", results.get(1).sigungu());
		assertEquals("돈암동", results.get(1).dong());
		server.verify();
	}

	@Test
	void rejectsMissingKeysBeforeSendingRequests() {
		KakaoLocationProperties properties = properties("  ", 1024);
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KakaoLocationSearchProvider provider = provider(builder.build(), properties);

		assertThrows(LocationProviderUnavailableException.class,
			() -> provider.search("학교", 10));
		server.verify();
	}

	@Test
	void hidesKeysAndQueriesWhenTheProviderFails() {
		KakaoLocationProperties properties = properties(REST_API_KEY, 1024);
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KakaoLocationSearchProvider provider = provider(builder.build(), properties);
		server.expect(request -> { }).andRespond(withServerError());

		LocationProviderUnavailableException exception = assertThrows(
			LocationProviderUnavailableException.class,
			() -> provider.search("private dormitory address", 10));

		assertFalse(exception.getMessage().contains(REST_API_KEY));
		assertFalse(exception.getMessage().contains("private dormitory address"));
		assertEquals(null, exception.getCause());
		server.verify();
	}

	@Test
	void rejectsMalformedAndOversizedResponses() {
		KakaoLocationProperties properties = properties(REST_API_KEY, 64);
		RestClient.Builder malformedBuilder = RestClient.builder();
		MockRestServiceServer malformedServer = MockRestServiceServer.bindTo(malformedBuilder).build();
		KakaoLocationSearchProvider malformed = provider(malformedBuilder.build(), properties);
		malformedServer.expect(request -> { })
			.andRespond(withSuccess("{not-json", MediaType.APPLICATION_JSON));
		assertThrows(LocationProviderUnavailableException.class,
			() -> malformed.search("학교", 10));
		malformedServer.verify();

		RestClient.Builder oversizedBuilder = RestClient.builder();
		MockRestServiceServer oversizedServer = MockRestServiceServer.bindTo(oversizedBuilder).build();
		KakaoLocationSearchProvider oversized = provider(oversizedBuilder.build(), properties);
		oversizedServer.expect(request -> { })
			.andRespond(withSuccess("x".repeat(65), MediaType.APPLICATION_JSON));
		assertThrows(LocationProviderUnavailableException.class,
			() -> oversized.search("학교", 10));
		oversizedServer.verify();
	}

	private static KakaoLocationSearchProvider provider(
		RestClient restClient,
		KakaoLocationProperties properties
	) {
		return new KakaoLocationSearchProvider(
			restClient, properties, JsonMapper.builder().build());
	}

	private static KakaoLocationProperties properties(String key, int maxResponseBytes) {
		return new KakaoLocationProperties(
			URI.create("https://dapi.kakao.com"),
			key,
			maxResponseBytes,
			Duration.ofSeconds(3),
			Duration.ofSeconds(5));
	}

	private static String decode(String value) {
		return URLDecoder.decode(value, StandardCharsets.UTF_8);
	}

	private static String addressResponse() {
		return """
			{
			  "meta": {"total_count": 1, "pageable_count": 1, "is_end": true},
			  "documents": [{
			    "address_name": "서울특별시 성북구 돈암동 173-1",
			    "x": "127.0165",
			    "y": "37.5928",
			    "address": {
			      "address_name": "서울특별시 성북구 돈암동 173-1",
			      "region_1depth_name": "서울특별시",
			      "region_2depth_name": "성북구",
			      "region_3depth_name": "돈암동"
			    },
			    "road_address": {
			      "address_name": "서울특별시 성북구 보문로34다길 2",
			      "region_1depth_name": "서울특별시",
			      "region_2depth_name": "성북구",
			      "region_3depth_name": "돈암동",
			      "building_name": "성신여자대학교"
			    }
			  }]
			}
			""";
	}

	private static String keywordResponse() {
		return """
			{
			  "meta": {"total_count": 1, "pageable_count": 1, "is_end": true},
			  "documents": [{
			    "id": "123456789",
			    "place_name": "성신여자대학교",
			    "address_name": "서울 성북구 돈암동 173-1",
			    "road_address_name": "서울 성북구 보문로34다길 2",
			    "x": "127.0165",
			    "y": "37.5928"
			  }]
			}
			""";
	}
}
