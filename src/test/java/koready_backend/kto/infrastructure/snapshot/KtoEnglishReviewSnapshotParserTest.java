package koready_backend.kto.infrastructure.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class KtoEnglishReviewSnapshotParserTest {

	@Test
	void readsOnlyRequestedEnglishItemsFromGzipSnapshot() throws Exception {
		byte[] compressed = gzip("""
			{
			  "response": {
			    "body": {
			      "items": {
			        "item": [
			          {
			            "contentid": "eng-1",
			            "contenttypeid": "12",
			            "title": "First place",
			            "addr1": "88 Test-ro",
			            "mapx": "126.98",
			            "mapy": "37.57",
			            "firstimage": "https://example.com/first.jpg"
			          },
			          {
			            "contentid": "eng-2",
			            "contenttypeid": "14",
			            "title": "Second place"
			          }
			        ]
			      }
			    }
			  }
			}
			""");
		var parser = new KtoEnglishReviewSnapshotParser(JsonMapper.builder().build());

		var result = parser.parse(
			new ByteArrayInputStream(compressed), List.of("eng-2"));

		assertEquals(1, result.size());
		assertEquals("Second place", result.get("eng-2").title());
		assertTrue(result.get("eng-2").sourceHash().matches("[a-f0-9]{64}"));
	}

	private static byte[] gzip(String value) throws Exception {
		var output = new ByteArrayOutputStream();
		try (var gzip = new GZIPOutputStream(output)) {
			gzip.write(value.getBytes(StandardCharsets.UTF_8));
		}
		return output.toByteArray();
	}
}
