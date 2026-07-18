package koready_backend.recommendation.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;
import koready_backend.recommendation.application.exception.InvalidDateRangeException;
import koready_backend.recommendation.application.exception.InvalidRecommendationCursorException;
import koready_backend.recommendation.application.port.MonthlyRecommendationRepository;
import koready_backend.recommendation.application.port.MonthlyRecommendationRepository.MonthlyRecommendationCursor;
import koready_backend.recommendation.application.port.MonthlyRecommendationRepository.MonthlyRecommendationFilter;
import koready_backend.recommendation.application.port.MonthlyRecommendationRepository.MonthlyRecommendationPageQuery;
import koready_backend.recommendation.application.port.MonthlyRecommendationRepository.MonthlyRecommendationRow;
import koready_backend.recommendation.domain.DateFilterType;
import koready_backend.recommendation.domain.FestivalOccurrenceStatus;
import koready_backend.recommendation.domain.RecommendationSort;

@Service
public class MonthlyRecommendationService {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final int MAX_CURSOR_LENGTH = 512;
	private static final int SHORT_DESCRIPTION_LENGTH = 160;

	private final MonthlyRecommendationRepository repository;
	private final Clock clock;

	@Autowired
	public MonthlyRecommendationService(MonthlyRecommendationRepository repository) {
		this(repository, Clock.system(SEOUL_ZONE));
	}

	MonthlyRecommendationService(MonthlyRecommendationRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	public MonthlyRecommendationPage getMonthlyRecommendations(
		int year,
		int month,
		ServiceRegionCode serviceRegionCode,
		DateFilterType dateFilterType,
		LocalDate customStartDate,
		LocalDate customEndDate,
		List<TravelStyle> travelStyles,
		RecommendationSort sort,
		String cursorToken,
		int size,
		PlaceLanguage language
	) {
		LocalDate today = LocalDate.now(clock);
		List<TravelStyle> normalizedStyles = travelStyles == null
			? List.of()
			: travelStyles.stream().distinct().sorted().toList();
		DateWindow selectedMonth = DateWindow.forMonth(year, month);
		DateWindow requestedWindow = requestedWindow(
			dateFilterType, customStartDate, customEndDate, today, selectedMonth);
		DateWindow effectiveWindow = selectedMonth.intersect(requestedWindow);
		AppliedFilters appliedFilters = new AppliedFilters(
			year,
			month,
			serviceRegionCode,
			dateFilterType,
			dateFilterType == DateFilterType.CUSTOM ? customStartDate : null,
			dateFilterType == DateFilterType.CUSTOM ? customEndDate : null,
			normalizedStyles,
			sort);
		String fingerprint = fingerprint(
			"MONTHLY",
			Integer.toString(year),
			Integer.toString(month),
			value(serviceRegionCode),
			dateFilterType.name(),
			value(customStartDate),
			value(customEndDate),
			normalizedStyles.toString(),
			sort.name(),
			language.name(),
			effectiveWindow == null ? "EMPTY" : effectiveWindow.toString());
		MonthlyRecommendationCursor cursor = decodeCursor(cursorToken, fingerprint, sort);

		if (effectiveWindow == null) {
			return new MonthlyRecommendationPage(
				year, month, appliedFilters, List.of(), null, false, 0L);
		}

		MonthlyRecommendationFilter filter = new MonthlyRecommendationFilter(
			effectiveWindow.startDate(),
			effectiveWindow.endDate(),
			today,
			serviceRegionCode,
			normalizedStyles,
			language,
			sort);
		List<MonthlyRecommendationRow> rows = repository.findPage(
			new MonthlyRecommendationPageQuery(filter, cursor, size + 1));
		long totalCount = repository.count(filter);
		boolean hasMore = rows.size() > size;
		List<MonthlyRecommendationRow> visibleRows =
			rows.subList(0, Math.min(size, rows.size()));
		List<PlaceCard> items = visibleRows.stream()
			.map(row -> card(row, language, today))
			.toList();
		String nextCursor = null;
		if (hasMore && !visibleRows.isEmpty()) {
			MonthlyRecommendationRow last = visibleRows.getLast();
			nextCursor = encodeCursor(fingerprint, sort, new MonthlyRecommendationCursor(
				last.statusRank(),
				last.qualityScore(),
				last.endDate(),
				last.occurrenceId()));
		}

		return new MonthlyRecommendationPage(
			year, month, appliedFilters, items, nextCursor, hasMore, totalCount);
	}

	private static DateWindow requestedWindow(
		DateFilterType filterType,
		LocalDate customStartDate,
		LocalDate customEndDate,
		LocalDate today,
		DateWindow selectedMonth
	) {
		if (filterType != DateFilterType.CUSTOM
			&& (customStartDate != null || customEndDate != null)) {
			throw new InvalidDateRangeException();
		}

		return switch (filterType) {
			case ALL -> selectedMonth;
			case THIS_WEEK -> {
				LocalDate monday = today.with(
					TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
				yield new DateWindow(monday, monday.plusDays(6));
			}
			case THIS_MONTH -> DateWindow.forMonth(today.getYear(), today.getMonthValue());
			case NEXT_MONTH -> {
				YearMonth nextMonth = YearMonth.from(today).plusMonths(1);
				yield DateWindow.forMonth(nextMonth.getYear(), nextMonth.getMonthValue());
			}
			case CUSTOM -> {
				if (customStartDate == null
					|| customEndDate == null
					|| customStartDate.isAfter(customEndDate)) {
					throw new InvalidDateRangeException();
				}
				yield new DateWindow(customStartDate, customEndDate);
			}
		};
	}

	private static PlaceCard card(
		MonthlyRecommendationRow row,
		PlaceLanguage language,
		LocalDate today
	) {
		FestivalOccurrenceStatus status = FestivalOccurrenceStatus.from(
			row.startDate(), row.endDate(), today);
		DateTimeFormatter formatter = language == PlaceLanguage.EN
			? DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH)
			: DateTimeFormatter.ofPattern("M.d");
		FestivalOccurrenceSummary occurrence = new FestivalOccurrenceSummary(
			row.occurrenceId(),
			row.eventYear(),
			row.startDate(),
			row.endDate(),
			status,
			formatter.format(row.startDate()) + " - " + formatter.format(row.endDate()));
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

	private static String encodeCursor(
		String fingerprint,
		RecommendationSort sort,
		MonthlyRecommendationCursor cursor
	) {
		String score = cursor.qualityScore() == null
			? "" : cursor.qualityScore().stripTrailingZeros().toPlainString();
		String endDate = cursor.endDate() == null ? "" : cursor.endDate().toString();
		String payload = String.join("\t",
			"1",
			fingerprint,
			sort.name(),
			Integer.toString(cursor.statusRank()),
			score,
			endDate,
			Long.toString(cursor.occurrenceId()));
		return Base64.getUrlEncoder().withoutPadding()
			.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
	}

	private static MonthlyRecommendationCursor decodeCursor(
		String token,
		String expectedFingerprint,
		RecommendationSort expectedSort
	) {
		if (token == null || token.isBlank()) {
			return null;
		}
		if (token.length() > MAX_CURSOR_LENGTH) {
			throw new InvalidRecommendationCursorException();
		}

		try {
			String payload = new String(
				Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
			String[] parts = payload.split("\t", -1);
			if (parts.length != 7
				|| !"1".equals(parts[0])
				|| !expectedFingerprint.equals(parts[1])
				|| !expectedSort.name().equals(parts[2])) {
				throw new InvalidRecommendationCursorException();
			}
			int statusRank = Integer.parseInt(parts[3]);
			BigDecimal score = parts[4].isBlank() ? null : new BigDecimal(parts[4]);
			LocalDate endDate = parts[5].isBlank() ? null : LocalDate.parse(parts[5]);
			long occurrenceId = Long.parseLong(parts[6]);
			if (statusRank < 0
				|| statusRank > 2
				|| occurrenceId <= 0
				|| (expectedSort == RecommendationSort.RECOMMENDED && score == null)
				|| (expectedSort == RecommendationSort.DEADLINE && endDate == null)) {
				throw new InvalidRecommendationCursorException();
			}
			return new MonthlyRecommendationCursor(statusRank, score, endDate, occurrenceId);
		} catch (InvalidRecommendationCursorException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new InvalidRecommendationCursorException();
		}
	}

	private static String value(Object value) {
		return value == null ? "" : value.toString();
	}

	private record DateWindow(LocalDate startDate, LocalDate endDate) {

		static DateWindow forMonth(int year, int month) {
			YearMonth value = YearMonth.of(year, month);
			return new DateWindow(value.atDay(1), value.atEndOfMonth());
		}

		DateWindow intersect(DateWindow other) {
			LocalDate intersectionStart = startDate.isAfter(other.startDate)
				? startDate : other.startDate;
			LocalDate intersectionEnd = endDate.isBefore(other.endDate)
				? endDate : other.endDate;
			return intersectionStart.isAfter(intersectionEnd)
				? null : new DateWindow(intersectionStart, intersectionEnd);
		}
	}

	public record MonthlyRecommendationPage(
		int year,
		int month,
		AppliedFilters appliedFilters,
		List<PlaceCard> items,
		String nextCursor,
		boolean hasMore,
		long totalCount
	) {
		public MonthlyRecommendationPage {
			items = List.copyOf(items);
		}
	}

	public record AppliedFilters(
		int year,
		int month,
		ServiceRegionCode serviceRegionCode,
		DateFilterType dateFilterType,
		LocalDate customStartDate,
		LocalDate customEndDate,
		List<TravelStyle> travelStyles,
		RecommendationSort sort
	) {
		public AppliedFilters {
			travelStyles = List.copyOf(travelStyles);
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
		public PlaceCard {
			tags = List.copyOf(tags);
		}
	}

	public record FestivalOccurrenceSummary(
		long occurrenceId,
		int eventYear,
		LocalDate startDate,
		LocalDate endDate,
		FestivalOccurrenceStatus status,
		String dateRangeText
	) {
	}
}
