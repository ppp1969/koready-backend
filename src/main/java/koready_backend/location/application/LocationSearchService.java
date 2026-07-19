package koready_backend.location.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import koready_backend.location.application.port.LocationSearchProvider;
import koready_backend.location.application.port.LocationSearchTokenCodec;
import koready_backend.location.domain.LocationSearchCandidate;
import koready_backend.location.domain.LocationSearchResultType;
import koready_backend.location.domain.LocationServiceRegionMapper;
import koready_backend.place.domain.ServiceRegionCode;

@Service
public class LocationSearchService {

	private static final Pattern WHITESPACE = Pattern.compile("\\s+");
	private static final int MAX_QUERY_LENGTH = 100;
	private static final int MAX_LIMIT = 20;

	private final LocationSearchProvider provider;
	private final LocationSearchTokenCodec tokenCodec;

	public LocationSearchService(
		LocationSearchProvider provider,
		LocationSearchTokenCodec tokenCodec
	) {
		this.provider = provider;
		this.tokenCodec = tokenCodec;
	}

	public SearchResponse search(String query, int limit) {
		String normalizedQuery = normalizeQuery(query);
		if (limit < 1 || limit > MAX_LIMIT) {
			throw new IllegalArgumentException("Location search limit is invalid");
		}

		List<LocationSearchCandidate> candidates = new ArrayList<>(
			Objects.requireNonNull(provider.search(normalizedQuery, limit)));
		candidates.sort(Comparator.comparing(candidate ->
			candidate.resultType() == LocationSearchResultType.PLACE ? 0 : 1));

		var selected = new ArrayList<MappedCandidate>();
		var placeIdentities = new HashSet<String>();
		var placeLocations = new HashSet<String>();
		var addressLocations = new HashSet<String>();
		for (LocationSearchCandidate candidate : candidates) {
			LocationServiceRegionMapper.fromSido(candidate.sido()).ifPresent(region -> {
				String locationKey = locationKey(candidate);
				if (candidate.resultType() == LocationSearchResultType.PLACE) {
					String identity = candidate.providerPlaceId() == null
						? locationKey + '|' + normalize(candidate.name())
						: candidate.providerPlaceId();
					if (placeIdentities.add(identity)) {
						selected.add(new MappedCandidate(candidate, region));
						placeLocations.add(locationKey);
					}
				} else if (!placeLocations.contains(locationKey)
					&& addressLocations.add(locationKey)) {
					selected.add(new MappedCandidate(candidate, region));
				}
			});
		}

		List<SearchItem> items = selected.stream()
			.limit(limit)
			.map(this::toItem)
			.toList();
		return new SearchResponse(items);
	}

	private SearchItem toItem(MappedCandidate mapped) {
		LocationSearchCandidate candidate = mapped.candidate();
		return new SearchItem(
			tokenCodec.issue(candidate, mapped.region()),
			candidate.resultType(),
			candidate.providerPlaceId(),
			candidate.name(),
			candidate.roadAddress(),
			candidate.address(),
			candidate.latitude(),
			candidate.longitude(),
			candidate.sido(),
			candidate.sigungu(),
			candidate.dong(),
			mapped.region());
	}

	private static String normalizeQuery(String query) {
		if (query == null) {
			throw new IllegalArgumentException("Location search query is required");
		}
		String normalized = WHITESPACE.matcher(query.strip()).replaceAll(" ");
		if (normalized.isEmpty() || normalized.length() > MAX_QUERY_LENGTH) {
			throw new IllegalArgumentException("Location search query is invalid");
		}
		return normalized;
	}

	private static String locationKey(LocationSearchCandidate candidate) {
		String address = candidate.roadAddress() != null
			? candidate.roadAddress()
			: candidate.address();
		return normalize(address)
			+ '|' + Math.round(candidate.latitude() * 100_000)
			+ '|' + Math.round(candidate.longitude() * 100_000);
	}

	private static String normalize(String value) {
		return value.toLowerCase(Locale.ROOT)
			.replace("서울특별시", "서울")
			.replace("부산광역시", "부산")
			.replace("대구광역시", "대구")
			.replace("인천광역시", "인천")
			.replace("광주광역시", "광주")
			.replace("대전광역시", "대전")
			.replace("울산광역시", "울산")
			.replace("세종특별자치시", "세종")
			.replace("경기도", "경기")
			.replace("강원특별자치도", "강원")
			.replace("강원도", "강원")
			.replace("충청북도", "충북")
			.replace("충청남도", "충남")
			.replace("전북특별자치도", "전북")
			.replace("전라북도", "전북")
			.replace("전라남도", "전남")
			.replace("경상북도", "경북")
			.replace("경상남도", "경남")
			.replace("제주특별자치도", "제주")
			.replaceAll("\\s+", "");
	}

	private record MappedCandidate(
		LocationSearchCandidate candidate,
		ServiceRegionCode region
	) {
	}

	public record SearchResponse(List<SearchItem> items) {

		public SearchResponse {
			items = List.copyOf(items);
		}
	}

	public record SearchItem(
		String searchResultToken,
		LocationSearchResultType resultType,
		String providerPlaceId,
		String name,
		String roadAddress,
		String address,
		double latitude,
		double longitude,
		String sido,
		String sigungu,
		String dong,
		ServiceRegionCode serviceRegionCode
	) {
	}
}
