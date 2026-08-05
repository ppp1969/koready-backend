package koready_backend.kto.domain;

import java.util.EnumSet;
import java.util.Set;

import koready_backend.place.domain.TravelStyle;

public final class KtoPlaceStyleRuleV1 {

	public static final String VERSION = "kto-place-style-v1";

	private static final Set<String> FOOD_CODES = Set.of("FD01", "FD02", "FD03");
	private static final Set<String> EXPERIENCE_CODES = Set.of(
		"EX01",
		"EX02",
		"EX03",
		"EX04");
	private static final Set<String> EXHIBITION_CODES = Set.of(
		"VE070100",
		"VE070300",
		"VE070500",
		"VE070600");

	public Set<TravelStyle> classify(KtoPlaceClassificationInput input) {
		EnumSet<TravelStyle> styles = EnumSet.noneOf(TravelStyle.class);
		if (input == null) {
			return Set.of();
		}

		if ("FD".equals(input.classificationCode1())
			&& FOOD_CODES.contains(input.classificationCode2())) {
			styles.add(TravelStyle.LOCAL_FOOD);
		}
		if ("EV".equals(input.classificationCode1())
			&& "EV01".equals(input.classificationCode2())) {
			styles.add(TravelStyle.LOCAL_FESTIVAL);
		}
		if ("SH".equals(input.classificationCode1())
			&& "SH06".equals(input.classificationCode2())) {
			styles.add(TravelStyle.TRADITIONAL_MARKET);
		}
		if ("EX".equals(input.classificationCode1())
			&& EXPERIENCE_CODES.contains(input.classificationCode2())) {
			styles.add(TravelStyle.CULTURE_EXPERIENCE);
		}
		if ("NA".equals(input.classificationCode1())) {
			styles.add(TravelStyle.NATURE);
		}
		if ("VE".equals(input.classificationCode1())
			&& "VE07".equals(input.classificationCode2())
			&& EXHIBITION_CODES.contains(input.classificationCode3())) {
			styles.add(TravelStyle.EXHIBITION_MUSEUM);
		}
		return Set.copyOf(styles);
	}
}
