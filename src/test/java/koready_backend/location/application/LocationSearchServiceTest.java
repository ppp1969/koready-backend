package koready_backend.location.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.location.application.port.LocationSearchProvider;
import koready_backend.location.application.port.EnglishLocationSearchProvider;
import koready_backend.location.application.port.LocationSearchTokenCodec;
import koready_backend.location.domain.LocationSearchCandidate;
import koready_backend.location.domain.LocationSearchResultType;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.ServiceRegionCode;

@ExtendWith(MockitoExtension.class)
class LocationSearchServiceTest {

	@Mock
	private LocationSearchProvider provider;

	@Mock
	private EnglishLocationSearchProvider englishProvider;

	@Mock
	private LocationSearchTokenCodec tokenCodec;

	@Test
	void normalizesTheQueryMapsRegionsDeduplicatesAndIssuesTokens() {
		LocationSearchCandidate addressDuplicate = candidate(
			LocationSearchResultType.ADDRESS, null, "성신여자대학교 주소", "서울특별시");
		LocationSearchCandidate place = candidate(
			LocationSearchResultType.PLACE, "123", "성신여자대학교", "서울특별시");
		LocationSearchCandidate unsupported = candidate(
			LocationSearchResultType.PLACE, "foreign", "Foreign place", "Tokyo");
		when(provider.search("성신 여자대학교", 10))
			.thenReturn(List.of(addressDuplicate, place, unsupported));
		when(tokenCodec.issue(any(), eq(ServiceRegionCode.SEOUL)))
			.thenAnswer(invocation -> "token-"
				+ invocation.getArgument(0, LocationSearchCandidate.class).name());

		var service = new LocationSearchService(provider, englishProvider, tokenCodec);
		var result = service.search("  성신   여자대학교  ", 10, PlaceLanguage.KO);

		assertEquals(1, result.items().size());
		var item = result.items().getFirst();
		assertEquals(LocationSearchResultType.PLACE, item.resultType());
		assertEquals("123", item.providerPlaceId());
		assertEquals(ServiceRegionCode.SEOUL, item.serviceRegionCode());
		assertEquals("token-성신여자대학교", item.searchResultToken());
		verify(provider).search("성신 여자대학교", 10);
	}

	@Test
	void appliesTheRequestedLimitAfterMerging() {
		when(provider.search("학교", 1)).thenReturn(List.of(
			candidate(LocationSearchResultType.PLACE, "1", "첫 번째 학교", "경기도"),
			candidate(LocationSearchResultType.PLACE, "2", "두 번째 학교", "강원특별자치도")));
		when(tokenCodec.issue(any(), any())).thenReturn("token");

		var result = new LocationSearchService(provider, englishProvider, tokenCodec)
			.search("학교", 1, PlaceLanguage.KO);

		assertEquals(1, result.items().size());
		assertEquals("첫 번째 학교", result.items().getFirst().name());
		assertEquals(ServiceRegionCode.GYEONGGI,
			result.items().getFirst().serviceRegionCode());
	}

	@Test
	void keepsDifferentPlacesAtTheSameAddressWhileDroppingTheAddressDuplicate() {
		LocationSearchCandidate first = candidate(
			LocationSearchResultType.PLACE, "place-1", "첫 번째 장소", "서울");
		LocationSearchCandidate second = candidate(
			LocationSearchResultType.PLACE, "place-2", "두 번째 장소", "서울특별시");
		LocationSearchCandidate address = candidate(
			LocationSearchResultType.ADDRESS, null, "같은 주소", "서울특별시");
		when(provider.search("같은 건물", 10)).thenReturn(List.of(address, first, second));
		when(tokenCodec.issue(any(), any())).thenReturn("token");

		var result = new LocationSearchService(provider, englishProvider, tokenCodec)
			.search("같은 건물", 10, PlaceLanguage.KO);

		assertEquals(List.of("place-1", "place-2"), result.items().stream()
			.map(LocationSearchService.SearchItem::providerPlaceId)
			.toList());
	}

	@Test
	void usesEnglishProviderAndPreservesEnglishDisplayFields() {
		LocationSearchCandidate english = new LocationSearchCandidate(
			"GOOGLE_PLACES",
			PlaceLanguage.EN,
			LocationSearchResultType.PLACE,
			"google-1",
			"Gyeongbokgung Palace",
			"161 Sajik-ro, Jongno-gu, Seoul",
			"161 Sajik-ro, Jongno-gu, Seoul",
			37.5796,
			126.9770,
			"Seoul",
			"Jongno-gu",
			null,
			"03045");
		when(englishProvider.search("Gyeongbokgung Palace", 10))
			.thenReturn(List.of(english));
		when(tokenCodec.issue(any(), eq(ServiceRegionCode.SEOUL)))
			.thenReturn("token");

		var result = new LocationSearchService(provider, englishProvider, tokenCodec)
			.search("Gyeongbokgung Palace", 10, PlaceLanguage.EN);

		assertEquals("GOOGLE_PLACES", result.items().getFirst().provider());
		assertEquals(PlaceLanguage.EN, result.items().getFirst().language());
		assertEquals("Gyeongbokgung Palace", result.items().getFirst().name());
		verify(englishProvider).search("Gyeongbokgung Palace", 10);
	}

	private static LocationSearchCandidate candidate(
		LocationSearchResultType type,
		String providerPlaceId,
		String name,
		String sido
	) {
		return new LocationSearchCandidate(
			type,
			providerPlaceId,
			name,
			"서울특별시 성북구 보문로34다길 2",
			"서울특별시 성북구 돈암동 173-1",
			37.5928,
			127.0165,
			sido,
			"성북구",
			"돈암동");
	}
}
