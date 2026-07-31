package koready_backend.buddy.application;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import koready_backend.buddy.domain.BuddyStyle;
import koready_backend.buddy.domain.KoreanLevel;
import koready_backend.buddy.domain.ProfileLanguage;
import koready_backend.buddy.domain.SocialLinkType;
import koready_backend.place.domain.TravelStyle;

@Service
public class ProfileOptionService {

	public ProfileOptions getOptions() {
		return new ProfileOptions(
			countries(),
			Arrays.stream(ProfileLanguage.values())
				.map(language -> new ProfileOption(
					language.name(),
					language.labelKo(),
					language.labelEn(),
					language.displayOrder()))
				.toList(),
			List.of(
				option(KoreanLevel.BEGINNER, "초급", "Beginner", 1),
				option(KoreanLevel.INTERMEDIATE, "중급", "Intermediate", 2),
				option(KoreanLevel.ADVANCED, "고급", "Advanced", 3)),
			List.of(
				option(TravelStyle.LOCAL_FOOD, "로컬 음식", "Local food", 1),
				option(TravelStyle.LOCAL_FESTIVAL, "지역 축제", "Local festivals", 2),
				option(TravelStyle.TRADITIONAL_MARKET, "전통시장", "Traditional markets", 3),
				option(TravelStyle.CULTURE_EXPERIENCE, "문화 체험", "Cultural experiences", 4),
				option(TravelStyle.NATURE, "자연", "Nature", 5),
				option(TravelStyle.EXHIBITION_MUSEUM, "전시·박물관", "Exhibitions and museums", 6),
				option(TravelStyle.DRAMA_LOCATION, "드라마 촬영지", "Drama locations", 7)),
			List.of(
				option(BuddyStyle.TRADITIONAL_CULTURE, "전통문화", "Traditional culture", 1),
				option(BuddyStyle.CAFE_TOUR, "카페 투어", "Cafe tour", 2),
				option(BuddyStyle.FOODIE, "맛집 탐방", "Foodie", 3),
				option(BuddyStyle.PHOTOGRAPHY, "사진", "Photography", 4),
				option(BuddyStyle.HANOK_EXPERIENCE, "한옥 체험", "Hanok experience", 5),
				option(BuddyStyle.QUIET_TRAVEL, "조용한 여행", "Quiet travel", 6)),
			List.of(
				option(SocialLinkType.INSTAGRAM, "인스타그램", "Instagram", 1),
				option(SocialLinkType.KAKAOTALK, "카카오톡", "KakaoTalk", 2),
				option(SocialLinkType.THREADS, "스레드", "Threads", 3),
				option(SocialLinkType.TIKTOK, "틱톡", "TikTok", 4),
				option(SocialLinkType.ETC, "기타", "Other", 5)));
	}

	private static List<CountryOption> countries() {
		List<CountryOption> sorted = Arrays.stream(Locale.getISOCountries())
			.map(code -> {
				Locale country = new Locale.Builder().setRegion(code).build();
				return new CountryOption(
					code,
					country.getDisplayCountry(Locale.KOREAN),
					country.getDisplayCountry(Locale.ENGLISH),
					0);
			})
			.sorted(Comparator.comparing(CountryOption::labelEn)
				.thenComparing(CountryOption::code))
			.toList();
		return java.util.stream.IntStream.range(0, sorted.size())
			.mapToObj(index -> new CountryOption(
				sorted.get(index).code(),
				sorted.get(index).labelKo(),
				sorted.get(index).labelEn(),
				index + 1))
			.toList();
	}

	private static ProfileOption option(
		Enum<?> value,
		String labelKo,
		String labelEn,
		int displayOrder
	) {
		return new ProfileOption(value.name(), labelKo, labelEn, displayOrder);
	}

	public record ProfileOptions(
		List<CountryOption> countries,
		List<ProfileOption> languages,
		List<ProfileOption> koreanLevels,
		List<ProfileOption> travelStyles,
		List<ProfileOption> buddyStyles,
		List<ProfileOption> socialPlatforms
	) {
	}

	public record ProfileOption(
		String code,
		String labelKo,
		String labelEn,
		int displayOrder
	) {
	}

	public record CountryOption(
		String code,
		String labelKo,
		String labelEn,
		int displayOrder
	) {
	}
}
