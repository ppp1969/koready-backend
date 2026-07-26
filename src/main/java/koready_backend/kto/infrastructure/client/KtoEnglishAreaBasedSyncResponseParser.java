package koready_backend.kto.infrastructure.client;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Component;

import koready_backend.kto.application.exception.KtoProviderException;
import koready_backend.kto.application.exception.KtoResponseParseException;
import koready_backend.kto.domain.KtoEnglishPlaceItem;
import koready_backend.kto.domain.KtoEnglishSyncPage;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public final class KtoEnglishAreaBasedSyncResponseParser {

	private static final String SUCCESS_CODE = "0000";

	private final JsonMapper jsonMapper;

	public KtoEnglishAreaBasedSyncResponseParser(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	KtoEnglishSyncPage parse(byte[] payload, int requestedPageSize) {
		try {
			JsonNode response = jsonMapper.readTree(payload).path("response");
			if (!response.isObject()) {
				throw new KtoResponseParseException("KTO English response envelope is missing");
			}
			String resultCode = requiredText(response.path("header"), "resultCode");
			if (!SUCCESS_CODE.equals(resultCode)) {
				throw new KtoProviderException(resultCode);
			}
			JsonNode body = response.path("body");
			if (!body.isObject()) {
				throw new KtoResponseParseException("KTO English response body is missing");
			}
			int responsePageSize = requiredInteger(body, "numOfRows");
			if (requestedPageSize < responsePageSize) {
				throw new KtoResponseParseException("KTO English response page size exceeded the request");
			}
			return new KtoEnglishSyncPage(
				requiredInteger(body, "pageNo"),
				requestedPageSize,
				requiredInteger(body, "totalCount"),
				parseItems(body.path("items")),
				payload.length,
				sha256(payload));
		} catch (KtoProviderException | KtoResponseParseException exception) {
			throw exception;
		} catch (JacksonException | IllegalArgumentException exception) {
			throw new KtoResponseParseException("KTO English response could not be parsed");
		}
	}

	private List<KtoEnglishPlaceItem> parseItems(JsonNode itemsNode) throws JacksonException {
		if (empty(itemsNode)) {
			return List.of();
		}
		if (!itemsNode.isObject()) {
			throw new KtoResponseParseException("KTO English items envelope is invalid");
		}
		JsonNode itemNode = itemsNode.path("item");
		if (empty(itemNode)) {
			return List.of();
		}
		if (itemNode.isObject()) {
			return List.of(parseItem(itemNode));
		}
		if (!itemNode.isArray()) {
			throw new KtoResponseParseException("KTO English item collection is invalid");
		}
		List<KtoEnglishPlaceItem> items = new ArrayList<>(itemNode.size());
		for (JsonNode item : itemNode) {
			items.add(parseItem(item));
		}
		return List.copyOf(items);
	}

	private KtoEnglishPlaceItem parseItem(JsonNode item) throws JacksonException {
		if (!item.isObject()) {
			throw new KtoResponseParseException("KTO English item is invalid");
		}
		String contentId = text(item, "contentid", "contentId");
		if (contentId == null) {
			throw new KtoResponseParseException("KTO English content id is missing");
		}
		return new KtoEnglishPlaceItem(
			contentId,
			text(item, "oldContentid", "oldcontentid", "oldContentId"),
			text(item, "contenttypeid", "contentTypeId"),
			text(item, "title"),
			text(item, "addr1"),
			text(item, "addr2"),
			text(item, "firstimage", "firstImage"),
			text(item, "firstimage2", "firstImage2"),
			text(item, "mapx", "mapX"),
			text(item, "mapy", "mapY"),
			text(item, "modifiedtime", "modifiedTime"),
			text(item, "showflag", "showFlag"),
			sha256(jsonMapper.writeValueAsBytes(item)));
	}

	private boolean empty(JsonNode node) {
		return node.isMissingNode() || node.isNull()
			|| (node.isString() && node.asString().isBlank());
	}

	private String requiredText(JsonNode node, String fieldName) {
		String value = text(node, fieldName);
		if (value == null) {
			throw new KtoResponseParseException("KTO English required field is missing");
		}
		return value;
	}

	private int requiredInteger(JsonNode node, String fieldName) {
		try {
			return Integer.parseInt(requiredText(node, fieldName));
		} catch (NumberFormatException exception) {
			throw new KtoResponseParseException("KTO English numeric field is invalid");
		}
	}

	private String text(JsonNode node, String... fieldNames) {
		for (String fieldName : fieldNames) {
			JsonNode valueNode = node.path(fieldName);
			if (!valueNode.isMissingNode() && !valueNode.isNull()) {
				String value = valueNode.asString();
				if (!value.isBlank()) {
					return value.trim();
				}
			}
		}
		return null;
	}

	private String sha256(byte[] payload) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}
}
