package koready_backend.route.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import koready_backend.place.domain.PlaceLanguage;
import koready_backend.route.application.exception.TransitProviderException;
import koready_backend.route.application.port.TransitRouteProvider.RouteProviderRequest;
import koready_backend.route.domain.RouteMode;
import koready_backend.route.infrastructure.config.TmapRouteProperties;
import tools.jackson.databind.json.JsonMapper;

class TmapTransitRouteProviderTest {

	private static final String APP_KEY = "test-tmap-app-key";

	@Test
	void requestsEnglishTransitRouteAndNormalizesSummaryAndLegs() {
		TmapRouteProperties properties = properties(APP_KEY);
		RestClient.Builder builder = RestClient.builder().baseUrl(properties.baseUrl().toString());
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		TmapTransitRouteProvider provider = provider(builder.build(), properties);

		server.expect(request -> {
			assertEquals(HttpMethod.POST, request.getMethod());
			assertEquals("/transit/routes", request.getURI().getPath());
			assertEquals(APP_KEY, request.getHeaders().getFirst("appKey"));
		}).andExpect(content().json("""
			{
			  "startX": 126.9770,
			  "startY": 37.5796,
			  "endX": 127.0276,
			  "endY": 37.4979,
			  "lang": 1,
			  "format": "json",
			  "count": 3,
			  "searchDttm": "202609011230"
			}
			"""))
			.andRespond(withSuccess(successResponse(), MediaType.APPLICATION_JSON));

		var route = provider.findRoutes(request(PlaceLanguage.EN)).getFirst();

		assertEquals(2_400, route.totalTimeSeconds());
		assertEquals(1, route.transferCount());
		assertEquals(1_500, route.totalFare());
		assertEquals(RouteMode.WALK, route.legs().get(0).mode());
		assertEquals(RouteMode.SUBWAY, route.legs().get(1).mode());
		assertEquals("Gyeongbokgung", route.legs().get(1).startName());
		assertEquals("Line 3", route.legs().get(1).routeName());
		server.verify();
	}

	@Test
	void requestsKoreanTransitRouteWithKoreanLanguageCode() {
		TmapRouteProperties properties = properties(APP_KEY);
		RestClient.Builder builder = RestClient.builder().baseUrl(properties.baseUrl().toString());
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		TmapTransitRouteProvider provider = provider(builder.build(), properties);

		server.expect(request -> { })
			.andExpect(content().string(org.hamcrest.Matchers.containsString("\"lang\":0")))
			.andRespond(withSuccess(successResponse(), MediaType.APPLICATION_JSON));

		provider.findRoutes(request(PlaceLanguage.KO));
		server.verify();
	}

	@Test
	void exposesNoRouteAndProviderFailureWithoutReturningFakeData() {
		TmapRouteProperties properties = properties(APP_KEY);
		RestClient.Builder noRouteBuilder = RestClient.builder();
		MockRestServiceServer noRouteServer = MockRestServiceServer.bindTo(noRouteBuilder).build();
		var noRouteProvider = provider(noRouteBuilder.build(), properties);
		noRouteServer.expect(request -> { }).andRespond(withSuccess(
			"{\"result\":{\"status\":11}}", MediaType.APPLICATION_JSON));
		assertEquals(0, noRouteProvider.findRoutes(request(PlaceLanguage.KO)).size());
		noRouteServer.verify();

		RestClient.Builder failureBuilder = RestClient.builder();
		MockRestServiceServer failureServer = MockRestServiceServer.bindTo(failureBuilder).build();
		var failureProvider = provider(failureBuilder.build(), properties);
		failureServer.expect(request -> { }).andRespond(withServerError());
		assertThrows(TransitProviderException.class,
			() -> failureProvider.findRoutes(request(PlaceLanguage.KO)));
		failureServer.verify();
	}

	private static RouteProviderRequest request(PlaceLanguage language) {
		return new RouteProviderRequest(
			37.5796, 126.9770, 37.4979, 127.0276,
			ZonedDateTime.of(2026, 9, 1, 12, 30, 0, 0,
				ZoneId.of("Asia/Seoul")),
			language);
	}

	private static TmapTransitRouteProvider provider(
		RestClient restClient,
		TmapRouteProperties properties
	) {
		return new TmapTransitRouteProvider(
			restClient, properties, JsonMapper.builder().build());
	}

	private static TmapRouteProperties properties(String appKey) {
		return new TmapRouteProperties(
			URI.create("https://apis.openapi.sk.com"),
			appKey,
			4 * 1024 * 1024,
			Duration.ofSeconds(3),
			Duration.ofSeconds(10));
	}

	private static String successResponse() {
		return """
			{
			  "metaData": {
			    "plan": {
			      "itineraries": [{
			        "totalTime": 2400,
			        "totalWalkTime": 300,
			        "totalWalkDistance": 400,
			        "transferCount": 1,
			        "fare": {"regular": {"totalFare": 1500}},
			        "legs": [
			          {
			            "mode": "WALK",
			            "start": {"name": "Gyeongbokgung Palace"},
			            "end": {"name": "Gyeongbokgung"},
			            "sectionTime": 300,
			            "distance": 400
			          },
			          {
			            "mode": "SUBWAY",
			            "start": {"name": "Gyeongbokgung"},
			            "end": {"name": "Gangnam"},
			            "route": "Line 3",
			            "sectionTime": 2100,
			            "distance": 12000,
			            "routePayment": 1500,
			            "service": 1
			          }
			        ]
			      }]
			    }
			  }
			}
			""";
	}
}
