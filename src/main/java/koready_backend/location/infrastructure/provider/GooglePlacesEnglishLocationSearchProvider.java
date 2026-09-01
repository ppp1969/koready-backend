package koready_backend.location.infrastructure.provider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;
import koready_backend.location.application.exception.LocationProviderUnavailableException;
import koready_backend.location.application.port.EnglishLocationSearchProvider;
import koready_backend.location.domain.LocationSearchCandidate;
import koready_backend.location.domain.LocationSearchResultType;
import koready_backend.location.infrastructure.config.GooglePlacesProperties;
import koready_backend.place.domain.PlaceLanguage;

@Component
@ConditionalOnProperty(
	name = "koready.location.search.provider",
	havingValue = "kakao")
public final class GooglePlacesEnglishLocationSearchProvider
	implements EnglishLocationSearchProvider {

	private static final String PATH = "/v1/places:searchText";
	private static final String FIELD_MASK = String.join(",",
		"places.id", "places.displayName", "places.formattedAddress",
		"places.shortFormattedAddress", "places.location", "places.addressComponents");
	private static final int READ_BUFFER_BYTES = 8 * 1024;

	private final RestClient restClient;
	private final GooglePlacesProperties properties;
	private final JsonMapper jsonMapper;

	public GooglePlacesEnglishLocationSearchProvider(
		@Qualifier("googlePlacesRestClient") RestClient restClient,
		GooglePlacesProperties properties,
		JsonMapper jsonMapper
	) {
		this.restClient = restClient;
		this.properties = properties;
		this.jsonMapper = jsonMapper;
	}

	@Override
	public List<LocationSearchCandidate> search(String query, int limit) {
		if (properties.apiKey().isBlank()) {
			throw new LocationProviderUnavailableException();
		}
		try {
			byte[] payload = restClient.post()
				.uri(PATH)
				.header("X-Goog-Api-Key", properties.apiKey())
				.header("X-Goog-FieldMask", FIELD_MASK)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.body(new SearchRequest(query, "en", "KR", limit))
				.exchange((request, response) -> {
					if (!response.getStatusCode().is2xxSuccessful()) {
						throw new LocationProviderUnavailableException();
					}
					if (response.getHeaders().getContentLength()
						> properties.maxResponseBytes()) {
						throw new LocationProviderUnavailableException();
					}
					return readBounded(response.getBody());
				});
			SearchResponse response = jsonMapper.readValue(payload, SearchResponse.class);
			if (response == null || response.places() == null) {
				return List.of();
			}
			return response.places().stream()
				.map(this::candidate)
				.filter(java.util.Objects::nonNull)
				.limit(limit)
				.toList();
		} catch (LocationProviderUnavailableException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new LocationProviderUnavailableException();
		}
	}

	private LocationSearchCandidate candidate(Place place) {
		try {
			if (place.id() == null || place.displayName() == null
				|| place.location() == null || place.formattedAddress() == null) {
				return null;
			}
			if (!hasCountry(place.addressComponents(), "KR")) {
				return null;
			}
			String sido = component(place.addressComponents(), "administrative_area_level_1");
			String sigungu = component(place.addressComponents(), "administrative_area_level_2");
			String dong = firstComponent(place.addressComponents(),
				"sublocality_level_1", "locality");
			String postalCode = component(place.addressComponents(), "postal_code");
			String shortAddress = blankToNull(place.shortFormattedAddress());
			return new LocationSearchCandidate(
				"GOOGLE_PLACES",
				PlaceLanguage.EN,
				LocationSearchResultType.PLACE,
				place.id(),
				place.displayName().text(),
				place.formattedAddress(),
				shortAddress == null ? place.formattedAddress() : shortAddress,
				place.location().latitude(),
				place.location().longitude(),
				sido,
				sigungu == null ? sido : sigungu,
				dong,
				postalCode);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private byte[] readBounded(InputStream input) throws IOException {
		var output = new ByteArrayOutputStream(
			Math.min(64 * 1024, properties.maxResponseBytes()));
		byte[] buffer = new byte[READ_BUFFER_BYTES];
		int total = 0;
		while (true) {
			int allowed = properties.maxResponseBytes() - total;
			int read = input.read(buffer, 0, Math.min(buffer.length, allowed + 1));
			if (read == -1) {
				return output.toByteArray();
			}
			if (read > allowed) {
				throw new LocationProviderUnavailableException();
			}
			output.write(buffer, 0, read);
			total += read;
		}
	}

	private static boolean hasCountry(List<AddressComponent> components, String country) {
		return components != null && components.stream()
			.anyMatch(item -> item.types() != null && item.types().contains("country")
				&& country.equalsIgnoreCase(item.shortText()));
	}

	private static String firstComponent(
		List<AddressComponent> components,
		String... types
	) {
		for (String type : types) {
			String value = component(components, type);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	private static String component(List<AddressComponent> components, String type) {
		if (components == null) {
			return null;
		}
		return components.stream()
			.filter(item -> item.types() != null && item.types().contains(type))
			.map(AddressComponent::longText)
			.map(GooglePlacesEnglishLocationSearchProvider::blankToNull)
			.filter(java.util.Objects::nonNull)
			.findFirst()
			.orElse(null);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	private record SearchRequest(
		String textQuery,
		String languageCode,
		String regionCode,
		int pageSize
	) {
	}

	private record SearchResponse(List<Place> places) {
	}

	private record Place(
		String id,
		LocalizedText displayName,
		String formattedAddress,
		String shortFormattedAddress,
		Location location,
		List<AddressComponent> addressComponents
	) {
	}

	private record LocalizedText(String text, String languageCode) {
	}

	private record Location(double latitude, double longitude) {
	}

	private record AddressComponent(
		String longText,
		String shortText,
		List<String> types,
		String languageCode
	) {
	}
}
