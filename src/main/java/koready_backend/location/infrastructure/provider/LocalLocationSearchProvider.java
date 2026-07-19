package koready_backend.location.infrastructure.provider;

import java.util.List;
import java.util.Locale;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import koready_backend.location.application.port.LocationSearchProvider;
import koready_backend.location.domain.LocationSearchCandidate;
import koready_backend.location.domain.LocationSearchResultType;

@Component
@ConditionalOnProperty(
	name = "koready.location.search.provider",
	havingValue = "local")
public final class LocalLocationSearchProvider implements LocationSearchProvider {

	private static final List<LocationSearchCandidate> FIXTURES = List.of(
		new LocationSearchCandidate(
			LocationSearchResultType.PLACE,
			"local-seongsin-university",
			"성신여자대학교",
			"서울특별시 성북구 보문로34다길 2",
			"서울특별시 성북구 돈암동 173-1",
			37.5928,
			127.0165,
			"서울특별시",
			"성북구",
			"돈암동"),
		new LocationSearchCandidate(
			LocationSearchResultType.ADDRESS,
			null,
			"성신여자대학교 주소",
			"서울특별시 성북구 보문로34다길 2",
			"서울특별시 성북구 돈암동 173-1",
			37.5928,
			127.0165,
			"서울특별시",
			"성북구",
			"돈암동"),
		new LocationSearchCandidate(
			LocationSearchResultType.PLACE,
			"local-hongik-university",
			"홍익대학교 서울캠퍼스",
			"서울특별시 마포구 와우산로 94",
			"서울특별시 마포구 상수동 72-1",
			37.5515,
			126.9250,
			"서울특별시",
			"마포구",
			"상수동"),
		new LocationSearchCandidate(
			LocationSearchResultType.PLACE,
			"local-jeonju-hanok-village",
			"전주한옥마을",
			"전북특별자치도 전주시 완산구 기린대로 99",
			"전북특별자치도 전주시 완산구 남노송동 100-1",
			35.8150,
			127.1530,
			"전북특별자치도",
			"전주시 완산구",
			"남노송동"));

	@Override
	public List<LocationSearchCandidate> search(String query, int limit) {
		String normalized = query.toLowerCase(Locale.KOREAN).replace(" ", "");
		return FIXTURES.stream()
			.filter(candidate -> searchable(candidate).contains(normalized))
			.limit(Math.max(limit, 1))
			.toList();
	}

	private static String searchable(LocationSearchCandidate candidate) {
		return (candidate.name()
			+ value(candidate.roadAddress())
			+ value(candidate.address())
			+ candidate.sido()
			+ candidate.sigungu()
			+ value(candidate.dong()))
			.toLowerCase(Locale.KOREAN)
			.replace(" ", "");
	}

	private static String value(String value) {
		return value == null ? "" : value;
	}
}
