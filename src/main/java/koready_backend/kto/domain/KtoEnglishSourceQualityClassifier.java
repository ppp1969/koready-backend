package koready_backend.kto.domain;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class KtoEnglishSourceQualityClassifier {

	public static final String VERSION = "kto-en-source-quality-v1";

	private static final List<String> ENCODING_MARKERS = List.of(
		"\uFFFD",
		"Ã",
		"Â",
		"â€",
		"ðŸ",
		"Ð",
		"Ñ");

	public Result classify(String title, String address) {
		String combined = value(title) + " " + value(address);
		if (hasEncodingMarker(combined)) {
			return result(
				KtoEnglishSourceQuality.ENCODING_SUSPECTED,
				KtoEnglishSourceQualityWarning.ENCODING_MARKER);
		}

		ScriptCounts titleScripts = countScripts(value(title));
		if (titleScripts.totalLetters() == 0) {
			ScriptCounts addressScripts = countScripts(value(address));
			if (addressScripts.latin() >= 3 && addressScripts.foreign() == 0) {
				return result(
					KtoEnglishSourceQuality.MIXED_OR_UNKNOWN,
					KtoEnglishSourceQualityWarning.INSUFFICIENT_TEXT);
			}
			return result(
				KtoEnglishSourceQuality.MIXED_OR_UNKNOWN,
				KtoEnglishSourceQualityWarning.INSUFFICIENT_TEXT);
		}
		if (titleScripts.latin() == 0) {
			return result(
				KtoEnglishSourceQuality.NON_ENGLISH_SUSPECTED,
				KtoEnglishSourceQualityWarning.NON_LATIN_TITLE);
		}
		if (titleScripts.foreign() > 0) {
			return result(
				KtoEnglishSourceQuality.MIXED_OR_UNKNOWN,
				KtoEnglishSourceQualityWarning.MIXED_SCRIPTS);
		}
		return new Result(KtoEnglishSourceQuality.USABLE, Set.of());
	}

	private static boolean hasEncodingMarker(String text) {
		return ENCODING_MARKERS.stream().anyMatch(text::contains);
	}

	private static ScriptCounts countScripts(String text) {
		int latin = 0;
		int companion = 0;
		int foreign = 0;
		for (int index = 0; index < text.length();) {
			int codePoint = text.codePointAt(index);
			index += Character.charCount(codePoint);
			if (!Character.isLetter(codePoint)) {
				continue;
			}
			Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
			if (script == Character.UnicodeScript.LATIN) {
				latin++;
			} else if (isExpectedCompanionScript(script)) {
				companion++;
			} else {
				foreign++;
			}
		}
		return new ScriptCounts(latin, companion, foreign);
	}

	private static boolean isExpectedCompanionScript(
		Character.UnicodeScript script
	) {
		return script == Character.UnicodeScript.HANGUL
			|| script == Character.UnicodeScript.HAN
			|| script == Character.UnicodeScript.HIRAGANA
			|| script == Character.UnicodeScript.KATAKANA;
	}

	private static String value(String text) {
		return text == null ? "" : text.strip();
	}

	private static Result result(
		KtoEnglishSourceQuality quality,
		KtoEnglishSourceQualityWarning warning
	) {
		return new Result(quality, EnumSet.of(warning));
	}

	public record Result(
		KtoEnglishSourceQuality quality,
		Set<KtoEnglishSourceQualityWarning> warnings
	) {
		public Result {
			warnings = Set.copyOf(warnings);
		}
	}

	private record ScriptCounts(int latin, int companion, int foreign) {
		private int totalLetters() {
			return latin + companion + foreign;
		}
	}
}
