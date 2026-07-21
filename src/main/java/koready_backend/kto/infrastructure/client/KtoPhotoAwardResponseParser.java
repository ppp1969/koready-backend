package koready_backend.kto.infrastructure.client;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import koready_backend.kto.application.exception.KtoProviderException;
import koready_backend.kto.application.exception.KtoResponseParseException;
import koready_backend.kto.domain.KtoPhotoAwardImage;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public final class KtoPhotoAwardResponseParser {
	private final JsonMapper jsonMapper;

	public KtoPhotoAwardResponseParser(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public Page parse(byte[] payload) {
		try {
			JsonNode response = jsonMapper.readTree(payload).path("response");
			String resultCode = text(response.path("header"), "resultCode");
			if (!"0000".equals(resultCode)) {
				throw new KtoProviderException(resultCode);
			}
			JsonNode body = response.path("body");
			int pageNo = integer(body, "pageNo");
			int rows = integer(body, "numOfRows");
			int total = integer(body, "totalCount");
			List<KtoPhotoAwardImage> images = new ArrayList<>();
			JsonNode item = body.path("items").path("item");
			if (item.isObject()) {
				item = jsonMapper.createArrayNode().add(item);
			}
			if (!item.isMissingNode() && !item.isNull() && !item.isString()) {
				int order = 1;
				for (JsonNode node : item) {
					String origin = text(node, "orgImage");
					if (origin != null) {
						images.add(new KtoPhotoAwardImage(
							text(node, "contentId"), origin, text(node, "thumbImage"),
							text(node, "koTitle"), text(node, "cpyrhtDivCd"),
							"1".equals(text(node, "showflag")), order));
					}
					order++;
				}
			}
			return new Page(pageNo, rows, total, List.copyOf(images));
		} catch (KtoProviderException | KtoResponseParseException exception) {
			throw exception;
		} catch (JacksonException | IllegalArgumentException exception) {
			throw new KtoResponseParseException("KTO photo award response could not be parsed");
		}
	}

	private int integer(JsonNode node, String name) {
		try { return Integer.parseInt(text(node, name)); }
		catch (RuntimeException exception) { throw new KtoResponseParseException("KTO photo award pagination is invalid"); }
	}

	private String text(JsonNode node, String name) {
		JsonNode value = node.path(name);
		if (value.isMissingNode() || value.isNull()) return null;
		String text = value.asString().strip();
		return text.isEmpty() ? null : text;
	}

	public record Page(int pageNumber, int pageSize, int totalCount, List<KtoPhotoAwardImage> images) { }
}
