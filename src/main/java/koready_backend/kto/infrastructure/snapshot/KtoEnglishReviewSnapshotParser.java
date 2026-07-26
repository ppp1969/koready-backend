package koready_backend.kto.infrastructure.snapshot;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.springframework.stereotype.Component;

import koready_backend.kto.domain.KtoEnglishPlaceItem;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
final class KtoEnglishReviewSnapshotParser {

	private final JsonMapper jsonMapper;

	KtoEnglishReviewSnapshotParser(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	Map<String, KtoEnglishPlaceItem> parse(
		InputStream compressed,
		Collection<String> requestedContentIds
	) {
		if (compressed == null || requestedContentIds == null
			|| requestedContentIds.isEmpty()) {
			return Map.of();
		}
		Set<String> requested = new HashSet<>(requestedContentIds);
		try (var gzip = new GZIPInputStream(compressed)) {
			JsonNode root = jsonMapper.readTree(gzip);
			JsonNode items = root.path("response").path("body").path("items").path("item");
			Map<String, KtoEnglishPlaceItem> found = new HashMap<>();
			if (items.isObject()) {
				add(items, requested, found);
			} else if (items.isArray()) {
				for (JsonNode item : items) {
					add(item, requested, found);
					if (found.size() == requested.size()) {
						break;
					}
				}
			}
			return Map.copyOf(found);
		} catch (IOException | JacksonException exception) {
			throw new IllegalStateException("KTO English review snapshot could not be read", exception);
		}
	}

	private void add(
		JsonNode item,
		Set<String> requested,
		Map<String, KtoEnglishPlaceItem> found
	) throws JacksonException {
		String contentId = text(item, "contentid", "contentId");
		if (contentId == null || !requested.contains(contentId)) {
			return;
		}
		found.put(contentId, new KtoEnglishPlaceItem(
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
			sha256(jsonMapper.writeValueAsBytes(item))));
	}

	private String text(JsonNode node, String... fieldNames) {
		for (String fieldName : fieldNames) {
			JsonNode value = node.path(fieldName);
			if (!value.isMissingNode() && !value.isNull()) {
				String text = value.asString();
				if (!text.isBlank()) {
					return text.trim();
				}
			}
		}
		return null;
	}

	private static String sha256(byte[] payload) {
		try {
			return HexFormat.of().formatHex(
				MessageDigest.getInstance("SHA-256").digest(payload));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}
}
