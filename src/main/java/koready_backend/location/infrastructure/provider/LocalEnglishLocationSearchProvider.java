package koready_backend.location.infrastructure.provider;

import java.util.List;
import java.util.Locale;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import koready_backend.location.application.port.EnglishLocationSearchProvider;
import koready_backend.location.domain.LocationSearchCandidate;
import koready_backend.location.domain.LocationSearchResultType;
import koready_backend.place.domain.PlaceLanguage;

@Component
@ConditionalOnProperty(
	name = "koready.location.search.provider",
	havingValue = "local")
public final class LocalEnglishLocationSearchProvider
	implements EnglishLocationSearchProvider {

	private static final List<LocationSearchCandidate> FIXTURES = List.of(
		new LocationSearchCandidate(
			"LOCAL",
			PlaceLanguage.EN,
			LocationSearchResultType.PLACE,
			"local-seongsin-university",
			"Sungshin Women's University",
			"2 Bomun-ro 34da-gil, Seongbuk-gu, Seoul",
			"2 Bomun-ro 34da-gil, Seongbuk-gu, Seoul",
			37.5928,
			127.0165,
			"Seoul",
			"Seongbuk-gu",
			null,
			"02844"));

	@Override
	public List<LocationSearchCandidate> search(String query, int limit) {
		String normalized = query.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
		return FIXTURES.stream()
			.filter(item -> (item.name() + item.roadAddress())
				.toLowerCase(Locale.ROOT).replaceAll("\\s+", "")
				.contains(normalized))
			.limit(limit)
			.toList();
	}
}
