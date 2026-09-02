package koready_backend.place.domain;

import java.util.regex.Pattern;

public final class EnglishPlaceTitleNormalizer {
	private static final Pattern TRAILING_KOREAN_ALIAS = Pattern.compile(
		"\\s*\\([^()]*[가-힣][^()]*\\)\\s*$");
	private static final Pattern LATIN = Pattern.compile("[A-Za-z]");

	private EnglishPlaceTitleNormalizer() {}

	public static String normalize(String title) {
		if (title == null || !LATIN.matcher(title).find()) return title;
		return TRAILING_KOREAN_ALIAS.matcher(title).replaceFirst("").strip();
	}
}
