package koready_backend.common.domain;

import java.util.regex.Pattern;

public final class EnglishPlaceTitleNormalizer {
	private static final Pattern LATIN = Pattern.compile("[A-Za-z]");
	private static final Pattern KOREAN = Pattern.compile("[가-힣]");

	private EnglishPlaceTitleNormalizer() {}

	public static String normalize(String title) {
		if (title == null || !LATIN.matcher(title).find()) return title;
		String normalized = title.strip();
		if (!normalized.endsWith(")")) return normalized;

		int depth = 0;
		for (int index = normalized.length() - 1; index >= 0; index--) {
			char current = normalized.charAt(index);
			if (current == ')') {
				depth++;
			} else if (current == '(') {
				depth--;
				if (depth == 0) {
					String trailingGroup = normalized.substring(index + 1, normalized.length() - 1);
					return KOREAN.matcher(trailingGroup).find()
						? normalized.substring(0, index).stripTrailing()
						: normalized;
				}
				if (depth < 0) return normalized;
			}
		}
		return normalized;
	}
}
