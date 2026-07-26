package koready_backend.kto.infrastructure.client;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import koready_backend.kto.application.exception.KtoProviderException;
import koready_backend.kto.application.exception.KtoResponseParseException;
import koready_backend.kto.domain.KtoDetailOperation;
import koready_backend.kto.domain.KtoDetailOperationResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public final class KtoDetailResponseParser {

	private static final String SUCCESS_CODE = "0000";

	private final JsonMapper jsonMapper;

	public KtoDetailResponseParser(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public KtoDetailOperationResponse parse(
		KtoDetailOperation operation,
		byte[] payload
	) {
		try {
			JsonNode response = jsonMapper.readTree(payload).path("response");
			if (!response.isObject()) {
				throw new KtoResponseParseException("KTO detail response envelope is missing");
			}
			String resultCode = text(response.path("header"), "resultCode");
			if (resultCode == null) {
				throw new KtoResponseParseException("KTO detail result code is missing");
			}
			if (!SUCCESS_CODE.equals(resultCode)) {
				throw new KtoProviderException(resultCode);
			}
			JsonNode body = response.path("body");
			if (!body.isObject()) {
				throw new KtoResponseParseException("KTO detail response body is missing");
			}
			return new KtoDetailOperationResponse(
				operation,
				items(body.path("items")),
				payload.length,
				sha256(payload));
		} catch (KtoProviderException | KtoResponseParseException exception) {
			throw exception;
		} catch (JacksonException | IllegalArgumentException exception) {
			throw new KtoResponseParseException("KTO detail response could not be parsed");
		}
	}

	private List<Map<String, String>> items(JsonNode itemsNode) {
		if (empty(itemsNode)) {
			return List.of();
		}
		if (!itemsNode.isObject()) {
			throw new KtoResponseParseException("KTO detail items envelope is invalid");
		}
		JsonNode itemNode = itemsNode.path("item");
		if (empty(itemNode)) {
			return List.of();
		}
		if (itemNode.isObject()) {
			return List.of(item(itemNode));
		}
		if (!itemNode.isArray()) {
			throw new KtoResponseParseException("KTO detail item collection is invalid");
		}
		var items = new ArrayList<Map<String, String>>();
		for (JsonNode node : itemNode) {
			if (!node.isObject()) {
				throw new KtoResponseParseException("KTO detail item is invalid");
			}
			items.add(item(node));
		}
		return List.copyOf(items);
	}

	private Map<String, String> item(JsonNode node) {
		var values = new LinkedHashMap<String, String>();
		for (var property : node.properties()) {
			JsonNode value = property.getValue();
			if (value.isNull() || value.isMissingNode()) {
				continue;
			}
			String text = value.asString().strip();
			if (!text.isEmpty()) {
				values.put(property.getKey(), text);
			}
		}
		return Map.copyOf(values);
	}

	private boolean empty(JsonNode node) {
		return node.isMissingNode()
			|| node.isNull()
			|| (node.isString() && node.asString().isBlank());
	}

	private String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (value.isMissingNode() || value.isNull()) {
			return null;
		}
		String text = value.asString().strip();
		return text.isEmpty() ? null : text;
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
