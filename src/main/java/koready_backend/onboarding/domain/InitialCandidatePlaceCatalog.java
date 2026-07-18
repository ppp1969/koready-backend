package koready_backend.onboarding.domain;

import java.util.List;

import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;

public final class InitialCandidatePlaceCatalog {

	private static final List<InitialCandidatePlace> APPROVED = List.of(
		place(
			1, "경복궁", "126508", "12", "경복궁", "Gyeongbokgung Palace",
			ServiceRegionCode.SEOUL, TravelStyle.CULTURE_EXPERIENCE,
			List.of("궁궐", "역사", "서울"),
			"조선의 중심 궁궐에서 한국의 역사와 건축을 함께 만나보세요.",
			"Discover Korean history and architecture at the main palace of the Joseon dynasty."),
		place(
			2, "광장시장", "132183", "38", "광장시장", "Gwangjang Market",
			ServiceRegionCode.SEOUL, TravelStyle.TRADITIONAL_MARKET,
			List.of("전통시장", "길거리음식", "서울"),
			"시장 골목을 걸으며 빈대떡과 마약김밥 같은 대표 먹거리를 경험해 보세요.",
			"Walk through a historic market and sample well-known Korean street foods."),
		place(
			3, "국립중앙박물관", "129703", "14", "국립중앙박물관", "National Museum of Korea",
			ServiceRegionCode.SEOUL, TravelStyle.EXHIBITION_MUSEUM,
			List.of("박물관", "한국사", "전시"),
			"선사 시대부터 근현대까지 한국 문화의 흐름을 한곳에서 살펴보세요.",
			"Explore the broad story of Korean history and culture in one place."),
		place(
			4, "한국민속촌", "125578", "12", "한국민속촌", "Korean Folk Village",
			ServiceRegionCode.GYEONGGI, TravelStyle.DRAMA_LOCATION,
			List.of("전통문화", "촬영지", "체험"),
			"전통 생활상을 재현한 공간에서 사극 촬영지와 다양한 체험을 즐겨보세요.",
			"Experience traditional life and familiar filming locations from Korean period dramas."),
		place(
			5, "경포해수욕장", "128758", "12", "경포해수욕장", "Gyeongpo Beach",
			ServiceRegionCode.GANGWON, TravelStyle.NATURE,
			List.of("바다", "해변", "강릉"),
			"넓은 백사장과 동해 풍경을 즐기며 강릉 여행의 여유를 느껴보세요.",
			"Enjoy an open sandy beach and the East Sea scenery in Gangneung."),
		place(
			6, "공산성", "125949", "12", "공주 공산성 [유네스코 세계유산]", "Gongsanseong Fortress",
			ServiceRegionCode.CHUNGCHEONG, TravelStyle.CULTURE_EXPERIENCE,
			List.of("세계유산", "백제", "성곽"),
			"백제의 역사가 남은 성곽길을 걸으며 공주의 풍경을 둘러보세요.",
			"Walk the fortress walls and discover the history of the Baekje kingdom."),
		place(
			7, "보령머드축제", "506534", "15", "보령머드축제", "Boryeong Mud Festival",
			ServiceRegionCode.CHUNGCHEONG, TravelStyle.LOCAL_FESTIVAL,
			List.of("지역축제", "머드체험", "보령"),
			"보령을 대표하는 여름 축제에서 머드를 활용한 참여형 프로그램을 즐겨보세요.",
			"Join hands-on mud activities at one of Korea's best-known summer festivals."),
		place(
			8, "전주 한옥마을", "264284", "12", "전북 전주 한옥마을 [슬로시티]", "Jeonju Hanok Village",
			ServiceRegionCode.JEOLLA, TravelStyle.LOCAL_FOOD,
			List.of("한옥", "전주음식", "골목여행"),
			"한옥 골목을 천천히 걸으며 전주의 다양한 향토 음식을 함께 만나보세요.",
			"Stroll through hanok streets while exploring Jeonju's distinctive local food."),
		place(
			9, "감천문화마을", "1997221", "12", "부산 감천문화마을", "Gamcheon Culture Village",
			ServiceRegionCode.GYEONGSANG, TravelStyle.DRAMA_LOCATION,
			List.of("마을여행", "촬영지", "부산"),
			"산비탈의 다채로운 골목과 전망을 따라 부산의 독특한 마을 풍경을 만나보세요.",
			"Follow colorful hillside alleys and familiar filming views across this Busan village."),
		place(
			10, "성산일출봉", "126435", "12", "성산일출봉 [유네스코 세계자연유산]", "Seongsan Ilchulbong",
			ServiceRegionCode.JEJU, TravelStyle.NATURE,
			List.of("세계자연유산", "오름", "제주"),
			"화산 지형이 만든 독특한 경관과 성산 일대의 탁 트인 풍경을 감상해 보세요.",
			"Take in a volcanic landscape and wide views over eastern Jeju."));

	private InitialCandidatePlaceCatalog() {
	}

	public static List<InitialCandidatePlace> approved() {
		return APPROVED;
	}

	private static InitialCandidatePlace place(
		int displayOrder,
		String searchKeyword,
		String contentId,
		String contentTypeId,
		String expectedTitleKo,
		String titleEn,
		ServiceRegionCode serviceRegionCode,
		TravelStyle travelStyle,
		List<String> tags,
		String curatorMessageKo,
		String curatorMessageEn
	) {
		return new InitialCandidatePlace(
			displayOrder,
			searchKeyword,
			contentId,
			contentTypeId,
			expectedTitleKo,
			titleEn,
			serviceRegionCode,
			travelStyle,
			tags,
			curatorMessageKo,
			curatorMessageEn,
			"KoReady 온보딩 대표 관광지 v1 운영 승인 목록");
	}
}
