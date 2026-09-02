package koready_backend.buddy.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProfileLanguageTest {
	@Test
	void supportsTheIsoLanguageCatalogBeyondTheOriginalThirteen() {
		assertEquals("SW", ProfileLanguage.valueOf("sw").name());
		assertEquals("Swahili", ProfileLanguage.valueOf("SW").labelEn());
		assertTrue(ProfileLanguage.values().length > 150);
	}
}
