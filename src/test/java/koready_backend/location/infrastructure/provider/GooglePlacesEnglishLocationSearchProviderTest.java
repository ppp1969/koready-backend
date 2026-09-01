package koready_backend.location.infrastructure.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;
import koready_backend.location.application.exception.LocationProviderUnavailableException;
import koready_backend.location.infrastructure.config.GooglePlacesProperties;
import koready_backend.place.domain.PlaceLanguage;

class GooglePlacesEnglishLocationSearchProviderTest {

	private static final String API_KEY = "test-google-places-api-key";

	@Test
	void requestsAndReturnsOfficialEnglishPlaceAndAddressFields() {
		GooglePlacesProperties properties = properties(API_KEY);
		RestClient.Builder builder = RestClient.builder().baseUrl(properties.baseUrl().toString());
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GooglePlacesEnglishLocationSearchProvider provider = provider(builder.build(), properties);

		server.expect(request -> {
			assertEquals(HttpMethod.POST, request.getMethod());
			assertEquals("/v1/places:searchText", request.getURI().getPath());
			assertEquals(API_KEY, request.getHeaders().getFirst("X-Goog-Api-Key"));
			String fieldMask = request.getHeaders().getFirst("X-Goog-FieldMask");
			assertFalse(fieldMask == null || !fieldMask.contains("places.formattedAddress"));
		}).andRespond(withSuccess(response(), MediaType.APPLICATION_JSON));

		var result = provider.search("Gyeongbokgung Palace", 10).getFirst();

		assertEquals("GOOGLE_PLACES", result.provider());
		assertEquals(PlaceLanguage.EN, result.language());
		assertEquals("Gyeongbokgung Palace", result.name());
		assertEquals("161 Sajik-ro, Jongno District, Seoul, South Korea", result.roadAddress());
		assertEquals("161 Sajik-ro, Jongno District, Seoul", result.address());
		assertEquals("Seoul", result.sido());
		assertEquals("Jongno District", result.sigungu());
		assertEquals("03045", result.postalCode());
		server.verify();
	}

	@Test
	void rejectsMissingKeyAndProviderFailureWithoutLeakingRequestData() {
		GooglePlacesProperties missingKeyProperties = properties("  ");
		RestClient.Builder missingKeyBuilder = RestClient.builder();
		MockRestServiceServer missingKeyServer = MockRestServiceServer.bindTo(missingKeyBuilder).build();
		var missingKeyProvider = provider(missingKeyBuilder.build(), missingKeyProperties);
		assertThrows(LocationProviderUnavailableException.class,
			() -> missingKeyProvider.search("private address", 10));
		missingKeyServer.verify();

		GooglePlacesProperties properties = properties(API_KEY);
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		var provider = provider(builder.build(), properties);
		server.expect(request -> { }).andRespond(withServerError());

		LocationProviderUnavailableException exception = assertThrows(
			LocationProviderUnavailableException.class,
			() -> provider.search("private address", 10));
		assertFalse(exception.getMessage().contains(API_KEY));
		assertFalse(exception.getMessage().contains("private address"));
		server.verify();
	}

	private static GooglePlacesEnglishLocationSearchProvider provider(
		RestClient restClient,
		GooglePlacesProperties properties
	) {
		return new GooglePlacesEnglishLocationSearchProvider(
			restClient, properties, JsonMapper.builder().build());
	}

	private static GooglePlacesProperties properties(String apiKey) {
		return new GooglePlacesProperties(
			URI.create("https://places.googleapis.com"),
			apiKey,
			512 * 1024,
			Duration.ofSeconds(3),
			Duration.ofSeconds(5));
	}

	private static String response() {
		return """
			{
			  "places": [{
			    "id": "ChIJod7tSseifDUR9hXHLFNGMIs",
			    "displayName": {"text": "Gyeongbokgung Palace", "languageCode": "en"},
			    "formattedAddress": "161 Sajik-ro, Jongno District, Seoul, South Korea",
			    "shortFormattedAddress": "161 Sajik-ro, Jongno District, Seoul",
			    "location": {"latitude": 37.5796, "longitude": 126.9770},
			    "addressComponents": [
			      {"longText": "Seoul", "shortText": "Seoul", "types": ["administrative_area_level_1"], "languageCode": "en"},
			      {"longText": "Jongno District", "shortText": "Jongno-gu", "types": ["administrative_area_level_2"], "languageCode": "en"},
			      {"longText": "03045", "shortText": "03045", "types": ["postal_code"], "languageCode": "en"},
			      {"longText": "South Korea", "shortText": "KR", "types": ["country"], "languageCode": "en"}
			    ]
			  }]
			}
			""";
	}
}
