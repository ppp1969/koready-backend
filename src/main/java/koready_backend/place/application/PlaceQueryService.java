package koready_backend.place.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import koready_backend.place.application.exception.InvalidPlaceCursorException;
import koready_backend.place.application.exception.PlaceNotFoundException;
import koready_backend.place.application.port.PlaceQueryRepository;
import koready_backend.place.application.port.PlaceQueryRepository.FestivalOccurrence;
import koready_backend.place.application.port.PlaceQueryRepository.PlaceCursor;
import koready_backend.place.application.port.PlaceQueryRepository.PlaceDetailRow;
import koready_backend.place.application.port.PlaceQueryRepository.PlaceListCriteria;
import koready_backend.place.application.port.PlaceQueryRepository.PlaceRow;
import koready_backend.place.application.port.PlaceQueryRepository.PlaceSearchCriteria;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.PlaceSort;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;

@Service
public class PlaceQueryService {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final int MAX_CURSOR_LENGTH = 512;
	private static final int SHORT_DESCRIPTION_LENGTH = 160;

	private final PlaceQueryRepository repository;
	private final Clock clock;

	@Autowired
	public PlaceQueryService(PlaceQueryRepository repository) {
		this(repository, Clock.system(SEOUL_ZONE));
	}

	PlaceQueryService(PlaceQueryRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	public PlacePage getPlaces(
		ServiceRegionCode serviceRegionCode,
		List<TravelStyle> travelStyles,
		PlaceSort sort,
		String cursorToken,
		int size,
		PlaceLanguage language
	) {
		List<TravelStyle> normalizedStyles = travelStyles == null
			? List.of()
			: travelStyles.stream().distinct().sorted().toList();
		String fingerprint = fingerprint(
			"LIST", serviceRegionCode.name(), normalizedStyles.toString(), sort.name(), language.name());
		PlaceCursor cursor = decodeCursor(cursorToken, fingerprint, sort);
		LocalDate today = LocalDate.now(clock);
		List<PlaceRow> rows = repository.findByRegion(new PlaceListCriteria(
			serviceRegionCode,
			normalizedStyles,
			sort,
			cursor,
			size + 1,
			language,
			today));

		return page(rows, size, fingerprint, sort, language, today);
	}

	public PlacePage search(
		String query,
		String cursorToken,
		int size,
		PlaceLanguage language
	) {
		String normalizedQuery = query.trim().replaceAll("\\s+", " ");
		String fingerprint = fingerprint("SEARCH", normalizedQuery, language.name());
		PlaceCursor cursor = decodeCursor(cursorToken, fingerprint, PlaceSort.RECOMMENDED);
		LocalDate today = LocalDate.now(clock);
		List<PlaceRow> rows = repository.search(new PlaceSearchCriteria(
			normalizedQuery,
			cursor,
			size + 1,
			language,
			today));

		return page(rows, size, fingerprint, PlaceSort.RECOMMENDED, language, today);
	}

	public PlaceDetail getPlace(long placeId, PlaceLanguage language) {
		PlaceDetailRow row = repository.findDetail(placeId, language)
			.orElseThrow(() -> new PlaceNotFoundException(placeId));
		PlaceDescription description = description(row);
		List<PlaceImage> images = row.imageUrl() == null
			? List.of()
			: List.of(new PlaceImage(row.imageUrl(), 1, row.title()));
		List<String> availableTabs = description == null ? List.of() : List.of("DESCRIPTION");

		return new PlaceDetail(
			row.placeId(),
			row.title(),
			row.serviceRegionCode(),
			row.serviceRegionName(),
			row.address(),
			row.latitude(),
			row.longitude(),
			null,
			null,
			null,
			null,
			null,
			images,
			List.of(),
			false,
			description,
			List.of(),
			availableTabs);
	}

	private PlacePage page(
		List<PlaceRow> rows,
		int size,
		String fingerprint,
		PlaceSort sort,
		PlaceLanguage language,
		LocalDate today
	) {
		boolean hasMore = rows.size() > size;
		List<PlaceRow> visibleRows = rows.subList(0, Math.min(size, rows.size()));
		List<PlaceCard> items = visibleRows.stream()
			.map(row -> card(row, language, today))
			.toList();
		String nextCursor = null;
		if (hasMore && !visibleRows.isEmpty()) {
			PlaceRow last = visibleRows.getLast();
			nextCursor = encodeCursor(
				fingerprint,
				sort,
				new PlaceCursor(last.qualityScore(), last.deadlineSortDate(), last.placeId()));
		}

		return new PlacePage(items, nextCursor, hasMore, null);
	}

	private PlaceCard card(PlaceRow row, PlaceLanguage language, LocalDate today) {
		FestivalOccurrenceSummary occurrence = occurrence(row.festivalOccurrence(), language, today);
		return new PlaceCard(
			row.placeId(),
			row.title(),
			row.serviceRegionCode(),
			row.serviceRegionName(),
			row.addressSummary(),
			row.imageUrl(),
			occurrence,
			row.travelStyle(),
			List.of(),
			shortDescription(row.overview()),
			false);
	}

	private static FestivalOccurrenceSummary occurrence(
		FestivalOccurrence occurrence,
		PlaceLanguage language,
		LocalDate today
	) {
		if (occurrence == null) {
			return null;
		}
		String status;
		if (today.isBefore(occurrence.startDate())) {
			status = "UPCOMING";
		} else if (today.isAfter(occurrence.endDate())) {
			status = "ENDED";
		} else {
			status = "ONGOING";
		}

		DateTimeFormatter formatter = language == PlaceLanguage.EN
			? DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH)
			: DateTimeFormatter.ofPattern("M.d");
		String range = formatter.format(occurrence.startDate())
			+ " - " + formatter.format(occurrence.endDate());
		return new FestivalOccurrenceSummary(
			occurrence.occurrenceId(),
			occurrence.eventYear(),
			occurrence.startDate(),
			occurrence.endDate(),
			status,
			range);
	}

	private static PlaceDescription description(PlaceDetailRow row) {
		if (row.overview() == null || row.overview().isBlank()) {
			return null;
		}
		List<String> paragraphs = new ArrayList<>();
		for (String paragraph : row.overview().strip().split("(?:\\R\\s*){2,}")) {
			if (!paragraph.isBlank()) {
				paragraphs.add(paragraph.strip());
			}
		}
		return new PlaceDescription(
			row.title(),
			row.address() == null ? row.serviceRegionName() : row.address(),
			paragraphs,
			List.of(),
			descriptionSource(row.translationSource()));
	}

	private static String descriptionSource(String translationSource) {
		return switch (translationSource) {
			case "AI_TRANSLATED" -> "AI_GENERATED";
			case "MANUAL_EDITED" -> "MANUAL_EDITED";
			default -> "KTO_ORIGINAL";
		};
	}

	private static String shortDescription(String overview) {
		if (overview == null || overview.isBlank()) {
			return null;
		}
		String normalized = overview.strip().replaceAll("\\s+", " ");
		if (normalized.length() <= SHORT_DESCRIPTION_LENGTH) {
			return normalized;
		}
		return normalized.substring(0, SHORT_DESCRIPTION_LENGTH - 3).stripTrailing() + "...";
	}

	private static String fingerprint(String... values) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (String value : values) {
				digest.update(Objects.requireNonNull(value).getBytes(StandardCharsets.UTF_8));
				digest.update((byte) 0);
			}
			return HexFormat.of().formatHex(digest.digest(), 0, 12);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private static String encodeCursor(String fingerprint, PlaceSort sort, PlaceCursor cursor) {
		String score = cursor.qualityScore() == null
			? "" : cursor.qualityScore().stripTrailingZeros().toPlainString();
		String deadline = cursor.deadlineSortDate() == null
			? "" : cursor.deadlineSortDate().toString();
		String payload = String.join("\t",
			"1", fingerprint, sort.name(), score, deadline, Long.toString(cursor.placeId()));
		return Base64.getUrlEncoder().withoutPadding()
			.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
	}

	private static PlaceCursor decodeCursor(
		String token,
		String expectedFingerprint,
		PlaceSort expectedSort
	) {
		if (token == null || token.isBlank()) {
			return null;
		}
		if (token.length() > MAX_CURSOR_LENGTH) {
			throw new InvalidPlaceCursorException();
		}

		try {
			String payload = new String(
				Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
			String[] parts = payload.split("\t", -1);
			if (parts.length != 6
				|| !"1".equals(parts[0])
				|| !expectedFingerprint.equals(parts[1])
				|| !expectedSort.name().equals(parts[2])) {
				throw new InvalidPlaceCursorException();
			}
			BigDecimal score = parts[3].isBlank() ? null : new BigDecimal(parts[3]);
			LocalDate deadline = parts[4].isBlank() ? null : LocalDate.parse(parts[4]);
			long placeId = Long.parseLong(parts[5]);
			if (placeId <= 0 || (expectedSort == PlaceSort.RECOMMENDED && score == null)) {
				throw new InvalidPlaceCursorException();
			}
			return new PlaceCursor(score, deadline, placeId);
		} catch (InvalidPlaceCursorException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new InvalidPlaceCursorException();
		}
	}

	public record PlacePage(
		List<PlaceCard> items,
		String nextCursor,
		boolean hasMore,
		Integer totalCount
	) {
		public PlacePage {
			items = List.copyOf(items);
		}
	}

	public record PlaceCard(
		long placeId,
		String title,
		ServiceRegionCode serviceRegionCode,
		String serviceRegionName,
		String addressSummary,
		String imageUrl,
		FestivalOccurrenceSummary festivalOccurrence,
		TravelStyle travelStyle,
		List<String> tags,
		String shortDescription,
		boolean saved
	) {
	}

	public record FestivalOccurrenceSummary(
		long occurrenceId,
		int eventYear,
		LocalDate startDate,
		LocalDate endDate,
		String status,
		String dateRangeText
	) {
	}

	public record PlaceDetail(
		long placeId,
		String title,
		ServiceRegionCode serviceRegionCode,
		String locationText,
		String address,
		BigDecimal latitude,
		BigDecimal longitude,
		String operatingHours,
		String operatingPeriod,
		String closedDays,
		String usageFee,
		String parkingInfo,
		List<PlaceImage> images,
		List<String> tags,
		boolean isSaved,
		PlaceDescription description,
		List<RelatedPlace> relatedPlaces,
		List<String> availableTabs
	) {
	}

	public record PlaceImage(String imageUrl, int order, String altText) {
	}

	public record PlaceDescription(
		String impactTitle,
		String impactSubtitle,
		List<String> introParagraphs,
		List<String> enjoyPoints,
		String sourceType
	) {
	}

	public record RelatedPlace(long placeId, String title, String imageUrl, String shortDescription) {
	}
}
