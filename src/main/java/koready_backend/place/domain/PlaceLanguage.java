package koready_backend.place.domain;

import java.util.List;
import java.util.Locale;

public enum PlaceLanguage {
	KO,
	EN;

	public static PlaceLanguage fromAcceptLanguage(String acceptLanguage) {
		if (acceptLanguage == null || acceptLanguage.isBlank()) {
			return KO;
		}

		try {
			List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(acceptLanguage);
			for (Locale.LanguageRange range : ranges) {
				String language = range.getRange().toLowerCase(Locale.ROOT);
				if (language.equals("en") || language.startsWith("en-")) {
					return EN;
				}
				if (language.equals("ko") || language.startsWith("ko-")) {
					return KO;
				}
			}
		} catch (IllegalArgumentException ignored) {
			return KO;
		}

		return KO;
	}
}
