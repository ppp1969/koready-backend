package koready_backend.kto.infrastructure.client;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Component;

import koready_backend.kto.application.exception.KtoProviderException;
import koready_backend.kto.application.exception.KtoResponseParseException;
import koready_backend.kto.domain.KtoPlaceDetail;
import koready_backend.kto.domain.KtoPlaceImage;
import koready_backend.kto.domain.KtoPlaceItem;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public final class KtoCuratedPlaceResponseParser {

	private static final String SUCCESS_CODE = "0000";

	private final JsonMapper jsonMapper;

	public KtoCuratedPlaceResponseParser(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public List<KtoPlaceItem> parseSearch(byte[] payload) {
		try {
			return parseItems(body(payload).path("items"));
		} catch (KtoProviderException | KtoResponseParseException exception) {
			throw exception;
		} catch (JacksonException | IllegalArgumentException exception) {
			throw new KtoResponseParseException("KTO curated search response could not be parsed");
		}
	}

	public KtoPlaceDetail parseDetail(byte[] payload) {
		try {
			List<JsonNode> items = itemNodes(body(payload).path("items"));
			if (items.size() != 1) {
				throw new KtoResponseParseException(
					"KTO curated detail response must contain exactly one item");
			}
			JsonNode item = items.getFirst();
			return new KtoPlaceDetail(
				parsePlace(item),
				text(item, "overview"),
				text(item, "homepage"));
		} catch (KtoProviderException | KtoResponseParseException exception) {
			throw exception;
		} catch (JacksonException | IllegalArgumentException exception) {
			throw new KtoResponseParseException("KTO curated detail response could not be parsed");
		}
	}

	public List<KtoPlaceImage> parseImages(byte[] payload) {
		try {
			List<KtoPlaceImage> images = new ArrayList<>();
			int fallbackOrder = 1;
			for (JsonNode item : itemNodes(body(payload).path("items"))) {
				String originImageUrl = text(item, "originimgurl");
				if (originImageUrl != null) {
					images.add(new KtoPlaceImage(
						originImageUrl,
						text(item, "smallimageurl"),
						text(item, "imgname"),
						text(item, "cpyrhtDivCd"),
						positiveNumber(text(item, "serialnum"), fallbackOrder)));
				}
				fallbackOrder++;
			}
			return List.copyOf(images);
		} catch (KtoProviderException | KtoResponseParseException exception) {
			throw exception;
		} catch (JacksonException | IllegalArgumentException exception) {
			throw new KtoResponseParseException("KTO curated detail image response could not be parsed");
		}
	}

	private JsonNode body(byte[] payload) throws JacksonException {
		JsonNode response = jsonMapper.readTree(payload).path("response");
		if (!response.isObject()) {
			throw new KtoResponseParseException("KTO curated response envelope is missing");
		}
		String resultCode = requiredText(response.path("header"), "resultCode");
		if (!SUCCESS_CODE.equals(resultCode)) {
			throw new KtoProviderException(resultCode);
		}
		JsonNode body = response.path("body");
		if (!body.isObject()) {
			throw new KtoResponseParseException("KTO curated response body is missing");
		}
		return body;
	}

	private List<KtoPlaceItem> parseItems(JsonNode itemsNode) throws JacksonException {
		List<KtoPlaceItem> items = new ArrayList<>();
		for (JsonNode item : itemNodes(itemsNode)) {
			items.add(parsePlace(item));
		}
		return List.copyOf(items);
	}

	private List<JsonNode> itemNodes(JsonNode itemsNode) {
		if (empty(itemsNode)) {
			return List.of();
		}
		if (!itemsNode.isObject()) {
			throw new KtoResponseParseException("KTO curated items envelope is invalid");
		}
		JsonNode itemNode = itemsNode.path("item");
		if (empty(itemNode)) {
			return List.of();
		}
		if (itemNode.isObject()) {
			return List.of(itemNode);
		}
		if (!itemNode.isArray()) {
			throw new KtoResponseParseException("KTO curated item collection is invalid");
		}
		List<JsonNode> items = new ArrayList<>(itemNode.size());
		for (JsonNode item : itemNode) {
			if (!item.isObject()) {
				throw new KtoResponseParseException("KTO curated item is invalid");
			}
			items.add(item);
		}
		return List.copyOf(items);
	}

	private KtoPlaceItem parsePlace(JsonNode item) throws JacksonException {
		if (!item.isObject()) {
			throw new KtoResponseParseException("KTO curated item is invalid");
		}
		return new KtoPlaceItem(
			requiredText(item, "contentid"),
			text(item, "contenttypeid"),
			text(item, "title"),
			text(item, "addr1"),
			text(item, "addr2"),
			text(item, "areacode"),
			text(item, "sigungucode"),
			text(item, "cat1"),
			text(item, "cat2"),
			text(item, "cat3"),
			text(item, "cpyrhtDivCd"),
			text(item, "createdtime"),
			text(item, "firstimage"),
			text(item, "firstimage2"),
			text(item, "mapx"),
			text(item, "mapy"),
			text(item, "mlevel"),
			text(item, "modifiedtime"),
			text(item, "tel"),
			text(item, "zipcode"),
			text(item, "showflag"),
			text(item, "lDongRegnCd"),
			text(item, "lDongSignguCd"),
			text(item, "lclsSystm1"),
			text(item, "lclsSystm2"),
			text(item, "lclsSystm3"),
			sha256(jsonMapper.writeValueAsBytes(item)));
	}

	private boolean empty(JsonNode node) {
		return node.isMissingNode()
			|| node.isNull()
			|| (node.isString() && node.asString().isBlank());
	}

	private String requiredText(JsonNode node, String fieldName) {
		String value = text(node, fieldName);
		if (value == null) {
			throw new KtoResponseParseException("KTO curated required field is missing");
		}
		return value;
	}

	private String text(JsonNode node, String fieldName) {
		JsonNode valueNode = node.path(fieldName);
		if (valueNode.isMissingNode() || valueNode.isNull()) {
			return null;
		}
		String value = valueNode.asString();
		return value.isBlank() ? null : value.strip();
	}

	private int positiveNumber(String value, int fallback) {
		if (value == null) {
			return fallback;
		}
		try {
			int parsed = Integer.parseInt(value);
			return parsed > 0 ? parsed : fallback;
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	private String sha256(byte[] payload) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}
}
