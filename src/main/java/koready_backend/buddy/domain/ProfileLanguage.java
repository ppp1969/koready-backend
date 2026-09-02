package koready_backend.buddy.domain;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(type = "string", pattern = "^[A-Z]{2}$", example = "SW",
	description = "ISO 639-1 구사 언어 코드. 앱 표시 언어와 별개입니다.")
public record ProfileLanguage(String code) {
	public static final ProfileLanguage KO = new ProfileLanguage("KO");
	public static final ProfileLanguage EN = new ProfileLanguage("EN");
	public static final ProfileLanguage ZH = new ProfileLanguage("ZH");
	public static final ProfileLanguage JA = new ProfileLanguage("JA");
	public static final ProfileLanguage VI = new ProfileLanguage("VI");
	public static final ProfileLanguage TH = new ProfileLanguage("TH");
	public static final ProfileLanguage MN = new ProfileLanguage("MN");
	public static final ProfileLanguage RU = new ProfileLanguage("RU");
	public static final ProfileLanguage ID = new ProfileLanguage("ID");
	public static final ProfileLanguage ES = new ProfileLanguage("ES");
	public static final ProfileLanguage FR = new ProfileLanguage("FR");
	public static final ProfileLanguage DE = new ProfileLanguage("DE");
	public static final ProfileLanguage AR = new ProfileLanguage("AR");

	private static final List<String> PRIMARY = List.of(
		"KO", "EN", "ZH", "JA", "VI", "TH", "MN", "RU", "ID", "ES", "FR", "DE", "AR");
	private static final Map<String, ProfileLanguage> SUPPORTED = supported();

	public ProfileLanguage {
		code = normalize(code);
		if (!Arrays.asList(Locale.getISOLanguages()).contains(code.toLowerCase(Locale.ROOT))) {
			throw new IllegalArgumentException("Unsupported ISO 639-1 language code: " + code);
		}
	}

	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public static ProfileLanguage valueOf(String value) {
		ProfileLanguage language = SUPPORTED.get(normalize(value));
		if (language == null) throw new IllegalArgumentException("Unsupported ISO 639-1 language code: " + value);
		return language;
	}

	public static ProfileLanguage[] values() { return SUPPORTED.values().toArray(ProfileLanguage[]::new); }
	@JsonValue public String name() { return code; }
	public String labelKo() { return locale().getDisplayLanguage(Locale.KOREAN); }
	public String labelEn() { return locale().getDisplayLanguage(Locale.ENGLISH); }
	public int displayOrder() { return new java.util.ArrayList<>(SUPPORTED.keySet()).indexOf(code) + 1; }

	private Locale locale() { return Locale.forLanguageTag(code.toLowerCase(Locale.ROOT)); }
	private static String normalize(String value) {
		if (value == null) throw new IllegalArgumentException("Language code is required");
		return value.trim().toUpperCase(Locale.ROOT);
	}
	private static Map<String, ProfileLanguage> supported() {
		var values = new LinkedHashMap<String, ProfileLanguage>();
		PRIMARY.forEach(code -> values.put(code, new ProfileLanguage(code)));
		Arrays.stream(Locale.getISOLanguages())
			.map(code -> code.toUpperCase(Locale.ROOT))
			.filter(code -> !values.containsKey(code))
			.sorted(Comparator.comparing(code -> Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH)))
			.forEach(code -> values.put(code, new ProfileLanguage(code)));
		return java.util.Collections.unmodifiableMap(values);
	}
}
