package koready_backend.kto.infrastructure.client;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Component;

import koready_backend.kto.application.exception.KtoProviderException;
import koready_backend.kto.application.exception.KtoResponseParseException;
import koready_backend.kto.domain.KtoPhotoAwardItem;
import koready_backend.kto.domain.KtoPhotoAwardPage;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public final class KtoPhotoAwardResponseParser {

	private static final String SUCCESS_CODE = "0000";

	private final JsonMapper jsonMapper;

	public KtoPhotoAwardResponseParser(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public KtoPhotoAwardPage parse(byte[] payload) {
		try {
			JsonNode response = jsonMapper.readTree(payload).path("response");
			if (!response.isObject()) {
				throw new KtoResponseParseException(
					"KTO photo award response envelope is missing");
			}
			String resultCode = requiredText(response.path("header"), "resultCode");
			if (!SUCCESS_CODE.equals(resultCode)) {
				throw new KtoProviderException(resultCode);
			}
			JsonNode body = response.path("body");
			if (!body.isObject()) {
				throw new KtoResponseParseException(
					"KTO photo award response body is missing");
			}
			return new KtoPhotoAwardPage(
				requiredInteger(body, "pageNo"),
				requiredInteger(body, "numOfRows"),
				requiredInteger(body, "totalCount"),
				parseItems(body.path("items")),
				payload.length,
				sha256(payload));
		} catch (KtoProviderException | KtoResponseParseException exception) {
			throw exception;
		} catch (JacksonException | IllegalArgumentException exception) {
			throw new KtoResponseParseException(
				"KTO photo award response could not be parsed");
		}
	}

	private List<KtoPhotoAwardItem> parseItems(JsonNode itemsNode)
		throws JacksonException {
		if (isEmpty(itemsNode)) {
			return List.of();
		}
		JsonNode itemNode = itemsNode.path("item");
		if (isEmpty(itemNode)) {
			return List.of();
		}
		if (itemNode.isObject()) {
			return List.of(parseItem(itemNode));
		}
		if (!itemNode.isArray()) {
			throw new KtoResponseParseException(
				"KTO photo award item collection is invalid");
		}
		List<KtoPhotoAwardItem> items = new ArrayList<>(itemNode.size());
		for (JsonNode item : itemNode) {
			items.add(parseItem(item));
		}
		return List.copyOf(items);
	}

	private KtoPhotoAwardItem parseItem(JsonNode item) throws JacksonException {
		if (!item.isObject()) {
			throw new KtoResponseParseException("KTO photo award item is invalid");
		}
		return new KtoPhotoAwardItem(
			requiredText(item, "contentId"),
			requiredText(item, "koTitle"),
			text(item, "koFilmst"),
			text(item, "koKeyWord"),
			text(item, "enTitle"),
			text(item, "enFilmst"),
			text(item, "enKeyWord"),
			requiredText(item, "orgImage"),
			text(item, "thumbImage"),
			text(item, "cpyrhtDivCd"),
			sha256(jsonMapper.writeValueAsBytes(item)));
	}

	private boolean isEmpty(JsonNode node) {
		return node.isMissingNode() || node.isNull()
			|| (node.isString() && node.asString().isBlank());
	}

	private String requiredText(JsonNode node, String fieldName) {
		String value = text(node, fieldName);
		if (value == null) {
			throw new KtoResponseParseException(
				"KTO photo award required field is missing");
		}
		return value;
	}

	private int requiredInteger(JsonNode node, String fieldName) {
		try {
			return Integer.parseInt(requiredText(node, fieldName));
		} catch (NumberFormatException exception) {
			throw new KtoResponseParseException(
				"KTO photo award numeric field is invalid");
		}
	}

	private String text(JsonNode node, String fieldName) {
		JsonNode valueNode = node.path(fieldName);
		if (valueNode.isMissingNode() || valueNode.isNull()) {
			return null;
		}
		String value = valueNode.asString();
		return value.isBlank() ? null : value.strip();
	}

	private String sha256(byte[] payload) {
		try {
			return HexFormat.of().formatHex(
				MessageDigest.getInstance("SHA-256").digest(payload));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}
}
