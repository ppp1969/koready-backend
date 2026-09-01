package koready_backend.route.infrastructure.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import koready_backend.place.domain.PlaceLanguage;
import koready_backend.route.application.exception.TransitProviderException;
import koready_backend.route.application.port.TransitRouteProvider;
import koready_backend.route.domain.RouteCandidate;
import koready_backend.route.domain.RouteCandidate.RouteLeg;
import koready_backend.route.domain.RouteMode;
import koready_backend.route.infrastructure.config.TmapRouteProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TmapTransitRouteProvider implements TransitRouteProvider {

	private static final String PATH = "/transit/routes";
	private static final DateTimeFormatter SEARCH_TIME =
		DateTimeFormatter.ofPattern("yyyyMMddHHmm");

	private final RestClient restClient;
	private final TmapRouteProperties properties;
	private final JsonMapper jsonMapper;

	public TmapTransitRouteProvider(
		@Qualifier("tmapRouteRestClient") RestClient restClient,
		TmapRouteProperties properties,
		JsonMapper jsonMapper
	) {
		this.restClient = restClient;
		this.properties = properties;
		this.jsonMapper = jsonMapper;
	}

	@Override
	public List<RouteCandidate> findRoutes(RouteProviderRequest request) {
		if (properties.appKey().isBlank()) {
			throw new TransitProviderException();
		}
		try {
			byte[] payload = restClient.post()
				.uri(PATH)
				.header("appKey", properties.appKey())
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.body(new TmapRequest(
					request.originLongitude(), request.originLatitude(),
					request.destinationLongitude(), request.destinationLatitude(),
					request.language() == PlaceLanguage.EN ? 1 : 0,
					"json", 3, SEARCH_TIME.format(request.departureAt())))
				.exchange((httpRequest, response) -> {
					if (!response.getStatusCode().is2xxSuccessful()) {
						throw new TransitProviderException();
					}
					long length = response.getHeaders().getContentLength();
					if (length > properties.maxResponseBytes()) {
						throw new TransitProviderException();
					}
					return readBounded(response.getBody());
				});
			return parse(payload);
		} catch (TransitProviderException exception) {
			throw exception;
		} catch (RestClientException | IOException exception) {
			throw new TransitProviderException();
		}
	}

	private List<RouteCandidate> parse(byte[] payload) throws IOException {
		JsonNode root = jsonMapper.readTree(payload);
		JsonNode result = root.path("result");
		if (!result.isMissingNode() && !result.isNull()) {
			int code = result.path("status").asInt(result.path("code").asInt(-1));
			if (code >= 11 && code <= 14) {
				return List.of();
			}
			throw new TransitProviderException();
		}
		JsonNode itineraries = root.path("metaData").path("plan").path("itineraries");
		if (!itineraries.isArray()) {
			return List.of();
		}
		List<RouteCandidate> candidates = new ArrayList<>();
		for (JsonNode itinerary : itineraries) {
			List<RouteLeg> legs = parseLegs(itinerary.path("legs"));
			if (legs.isEmpty()) {
				continue;
			}
			JsonNode totalFare = itinerary.path("fare").path("regular").path("totalFare");
			candidates.add(new RouteCandidate(
				itinerary.path("totalTime").asInt(),
				itinerary.path("totalWalkTime").asInt(),
				itinerary.path("totalWalkDistance").asInt(),
				itinerary.path("transferCount").asInt(),
				totalFare.isNumber() ? totalFare.asInt() : null,
				legs));
		}
		return List.copyOf(candidates);
	}

	private static List<RouteLeg> parseLegs(JsonNode nodes) {
		if (!nodes.isArray()) {
			return List.of();
		}
		List<RouteLeg> legs = new ArrayList<>();
		for (JsonNode node : nodes) {
			RouteMode mode = mode(node.path("mode").asText());
			JsonNode payment = node.path("routePayment");
			legs.add(new RouteLeg(
				mode,
				text(node.path("start").path("name"), "출발"),
				text(node.path("end").path("name"), "도착"),
				blankToNull(node.path("route").asText(null)),
				node.path("sectionTime").asInt(),
				node.path("distance").asInt(),
				payment.isNumber() ? payment.asInt() : null,
				mode == RouteMode.WALK || node.path("service").asInt(1) == 1));
		}
		return List.copyOf(legs);
	}

	private static RouteMode mode(String value) {
		try {
			return RouteMode.valueOf(value.toUpperCase(Locale.ROOT)
				.replace("EXPRESSBUS", "EXPRESS_BUS"));
		} catch (RuntimeException exception) {
			throw new TransitProviderException();
		}
	}

	private static String text(JsonNode node, String fallback) {
		String value = node.asText();
		return value == null || value.isBlank() ? fallback : value.strip();
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	private byte[] readBounded(InputStream input) throws IOException {
		var output = new ByteArrayOutputStream(Math.min(64 * 1024,
			properties.maxResponseBytes()));
		byte[] buffer = new byte[8 * 1024];
		int total = 0;
		while (true) {
			int read = input.read(buffer, 0,
				Math.min(buffer.length, properties.maxResponseBytes() - total + 1));
			if (read == -1) {
				return output.toByteArray();
			}
			if (read > properties.maxResponseBytes() - total) {
				throw new TransitProviderException();
			}
			output.write(buffer, 0, read);
			total += read;
		}
	}

	private record TmapRequest(
		double startX,
		double startY,
		double endX,
		double endY,
		int lang,
		String format,
		int count,
		String searchDttm
	) {
	}
}
