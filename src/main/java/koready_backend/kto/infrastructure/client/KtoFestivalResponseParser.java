package koready_backend.kto.infrastructure.client;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Component;

import koready_backend.kto.application.exception.KtoProviderException;
import koready_backend.kto.application.exception.KtoResponseParseException;
import koready_backend.kto.domain.KtoFestivalItem;
import koready_backend.kto.domain.KtoFestivalPage;
import koready_backend.kto.domain.KtoPlaceItem;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public final class KtoFestivalResponseParser {

	private static final String SUCCESS_CODE = "0000";
	private static final DateTimeFormatter KTO_DATE = DateTimeFormatter.BASIC_ISO_DATE;

	private final JsonMapper jsonMapper;

	public KtoFestivalResponseParser(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public KtoFestivalPage parse(byte[] payload) {
		try {
			JsonNode response = jsonMapper.readTree(payload).path("response");
			if (!response.isObject()) {
				throw new KtoResponseParseException("KTO festival response envelope is missing");
			}

			String resultCode = requiredText(response.path("header"), "resultCode");
			if (!SUCCESS_CODE.equals(resultCode)) {
				throw new KtoProviderException(resultCode);
			}

			JsonNode body = response.path("body");
			if (!body.isObject()) {
				throw new KtoResponseParseException("KTO festival response body is missing");
			}

			return new KtoFestivalPage(
				requiredInteger(body, "pageNo"),
				requiredInteger(body, "numOfRows"),
				requiredInteger(body, "totalCount"),
				parseItems(body.path("items")),
				payload.length,
				sha256(payload));
		} catch (KtoProviderException | KtoResponseParseException exception) {
			throw exception;
		} catch (JacksonException | IllegalArgumentException exception) {
			throw new KtoResponseParseException("KTO festival response could not be parsed");
		}
	}

	private List<KtoFestivalItem> parseItems(JsonNode itemsNode) throws JacksonException {
		if (isEmptyNode(itemsNode)) {
			return List.of();
		}
		if (!itemsNode.isObject()) {
			throw new KtoResponseParseException("KTO festival items envelope is invalid");
		}

		JsonNode itemNode = itemsNode.path("item");
		if (isEmptyNode(itemNode)) {
			return List.of();
		}
		if (itemNode.isObject()) {
			return List.of(parseItem(itemNode));
		}
		if (!itemNode.isArray()) {
			throw new KtoResponseParseException("KTO festival item collection is invalid");
		}

		List<KtoFestivalItem> items = new ArrayList<>(itemNode.size());
		for (JsonNode item : itemNode) {
			items.add(parseItem(item));
		}
		return List.copyOf(items);
	}

	private KtoFestivalItem parseItem(JsonNode item) throws JacksonException {
		if (!item.isObject()) {
			throw new KtoResponseParseException("KTO festival item is invalid");
		}

		String contentId = requiredText(item, "contentid");
		KtoPlaceItem place = new KtoPlaceItem(
			contentId,
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
			null,
			text(item, "lDongRegnCd"),
			text(item, "lDongSignguCd"),
			text(item, "lclsSystm1"),
			text(item, "lclsSystm2"),
			text(item, "lclsSystm3"),
			sha256(jsonMapper.writeValueAsBytes(item)));

		return new KtoFestivalItem(
			place,
			date(item, "eventstartdate"),
			date(item, "eventenddate"),
			text(item, "progresstype"),
			text(item, "festivaltype"));
	}

	private LocalDate date(JsonNode node, String fieldName) {
		String value = requiredText(node, fieldName);
		try {
			return LocalDate.parse(value, KTO_DATE);
		} catch (DateTimeParseException exception) {
			throw new KtoResponseParseException("KTO festival date is invalid");
		}
	}

	private boolean isEmptyNode(JsonNode node) {
		return node.isMissingNode()
			|| node.isNull()
			|| (node.isString() && node.asString().isBlank());
	}

	private String requiredText(JsonNode node, String fieldName) {
		String value = text(node, fieldName);
		if (value == null) {
			throw new KtoResponseParseException("KTO festival required field is missing");
		}
		return value;
	}

	private int requiredInteger(JsonNode node, String fieldName) {
		String value = requiredText(node, fieldName);
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException exception) {
			throw new KtoResponseParseException("KTO festival numeric field is invalid");
		}
	}

	private String text(JsonNode node, String fieldName) {
		JsonNode valueNode = node.path(fieldName);
		if (valueNode.isMissingNode() || valueNode.isNull()) {
			return null;
		}
		String value = valueNode.asString();
		return value.isBlank() ? null : value.trim();
	}

	private String sha256(byte[] payload) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}
}
