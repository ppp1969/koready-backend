package koready_backend.location.infrastructure.provider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;
import koready_backend.location.application.exception.LocationProviderUnavailableException;
import koready_backend.location.application.port.LocationSearchProvider;
import koready_backend.location.domain.LocationSearchCandidate;
import koready_backend.location.domain.LocationSearchResultType;
import koready_backend.location.infrastructure.config.KakaoLocationProperties;

@Component
@ConditionalOnProperty(
	name = "koready.location.search.provider",
	havingValue = "kakao")
public final class KakaoLocationSearchProvider implements LocationSearchProvider {

	private static final String ADDRESS_PATH = "/v2/local/search/address.json";
	private static final String KEYWORD_PATH = "/v2/local/search/keyword.json";
	private static final int ADDRESS_MAX_SIZE = 30;
	private static final int KEYWORD_MAX_SIZE = 15;
	private static final int READ_BUFFER_BYTES = 8 * 1024;

	private final RestClient restClient;
	private final KakaoLocationProperties properties;
	private final JsonMapper jsonMapper;

	public KakaoLocationSearchProvider(
		@Qualifier("kakaoLocationRestClient") RestClient restClient,
		KakaoLocationProperties properties,
		JsonMapper jsonMapper
	) {
		this.restClient = restClient;
		this.properties = properties;
		this.jsonMapper = jsonMapper;
	}

	@Override
	public List<LocationSearchCandidate> search(String query, int limit) {
		if (properties.restApiKey().isBlank()) {
			throw new LocationProviderUnavailableException();
		}
		if (query == null || query.isBlank() || limit < 1) {
			throw new IllegalArgumentException("Kakao Location search request is invalid");
		}

		try {
			var results = new ArrayList<LocationSearchCandidate>();
			results.addAll(parseAddress(fetch(
				ADDRESS_PATH, query, Math.min(limit, ADDRESS_MAX_SIZE))));
			results.addAll(parseKeyword(fetch(
				KEYWORD_PATH, query, Math.min(limit, KEYWORD_MAX_SIZE))));
			return List.copyOf(results);
		} catch (LocationProviderUnavailableException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new LocationProviderUnavailableException();
		}
	}

	private byte[] fetch(String path, String query, int size) {
		return restClient.get()
			.uri(uriBuilder -> uriBuilder
				.path(path)
				.queryParam("query", query)
				.queryParam("size", size)
				.build())
			.header(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.restApiKey())
			.accept(MediaType.APPLICATION_JSON)
			.exchange((request, response) -> {
				if (!response.getStatusCode().is2xxSuccessful()) {
					throw new LocationProviderUnavailableException();
				}
				long contentLength = response.getHeaders().getContentLength();
				if (contentLength > properties.maxResponseBytes()) {
					throw new LocationProviderUnavailableException();
				}
				return readBounded(response.getBody());
			});
	}

	private List<LocationSearchCandidate> parseAddress(byte[] payload) throws IOException {
		KakaoSearchResponse response = jsonMapper.readValue(payload, KakaoSearchResponse.class);
		if (response == null || response.documents() == null) {
			throw new LocationProviderUnavailableException();
		}
		return response.documents().stream()
			.map(this::addressCandidate)
			.filter(java.util.Objects::nonNull)
			.toList();
	}

	private List<LocationSearchCandidate> parseKeyword(byte[] payload) throws IOException {
		KakaoSearchResponse response = jsonMapper.readValue(payload, KakaoSearchResponse.class);
		if (response == null || response.documents() == null) {
			throw new LocationProviderUnavailableException();
		}
		return response.documents().stream()
			.map(this::keywordCandidate)
			.filter(java.util.Objects::nonNull)
			.toList();
	}

	private LocationSearchCandidate addressCandidate(KakaoDocument document) {
		try {
			KakaoAddress region = document.address() != null
				? document.address()
				: document.road_address();
			if (region == null) {
				return null;
			}
			String roadAddress = document.road_address() == null
				? null
				: document.road_address().address_name();
			String address = document.address() == null
				? document.address_name()
				: document.address().address_name();
			String name = firstNonBlank(
				document.road_address() == null
					? null
					: document.road_address().building_name(),
				roadAddress,
				address);
			return new LocationSearchCandidate(
				LocationSearchResultType.ADDRESS,
				null,
				name,
				roadAddress,
				address,
				coordinate(document.y()),
				coordinate(document.x()),
				region.region_1depth_name(),
				region.region_2depth_name(),
				region.region_3depth_name());
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private LocationSearchCandidate keywordCandidate(KakaoDocument document) {
		try {
			RegionParts region = parseRegion(document.address_name());
			return new LocationSearchCandidate(
				LocationSearchResultType.PLACE,
				document.id(),
				document.place_name(),
				document.road_address_name(),
				document.address_name(),
				coordinate(document.y()),
				coordinate(document.x()),
				region.sido(),
				region.sigungu(),
				region.dong());
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private byte[] readBounded(InputStream input) throws IOException {
		var output = new ByteArrayOutputStream(
			Math.min(64 * 1024, properties.maxResponseBytes()));
		byte[] buffer = new byte[READ_BUFFER_BYTES];
		int totalBytes = 0;
		while (true) {
			int allowed = properties.maxResponseBytes() - totalBytes;
			int bytesRead = input.read(buffer, 0, Math.min(buffer.length, allowed + 1));
			if (bytesRead == -1) {
				return output.toByteArray();
			}
			if (bytesRead > allowed) {
				throw new LocationProviderUnavailableException();
			}
			output.write(buffer, 0, bytesRead);
			totalBytes += bytesRead;
		}
	}

	private static RegionParts parseRegion(String address) {
		if (address == null) {
			throw new IllegalArgumentException("Kakao address is missing");
		}
		String[] parts = Arrays.stream(address.strip().split("\\s+"))
			.filter(part -> !part.isBlank())
			.toArray(String[]::new);
		if (parts.length < 2) {
			throw new IllegalArgumentException("Kakao address region is invalid");
		}
		boolean compoundDistrict = parts.length >= 3
			&& parts[1].endsWith("시") && parts[2].endsWith("구");
		String sigungu = compoundDistrict ? parts[1] + ' ' + parts[2] : parts[1];
		int dongIndex = compoundDistrict ? 3 : 2;
		String dong = parts.length > dongIndex ? parts[dongIndex] : null;
		return new RegionParts(parts[0], sigungu, dong);
	}

	private static double coordinate(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Kakao coordinate is missing");
		}
		return Double.parseDouble(value);
	}

	private static String firstNonBlank(String... values) {
		return Arrays.stream(values)
			.filter(value -> value != null && !value.isBlank())
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Kakao location name is missing"));
	}

	private record RegionParts(String sido, String sigungu, String dong) {
	}

	private record KakaoSearchResponse(List<KakaoDocument> documents) {
	}

	private record KakaoDocument(
		String id,
		String place_name,
		String address_name,
		String road_address_name,
		String x,
		String y,
		KakaoAddress address,
		KakaoAddress road_address
	) {
	}

	private record KakaoAddress(
		String address_name,
		String region_1depth_name,
		String region_2depth_name,
		String region_3depth_name,
		String building_name
	) {
	}
}
