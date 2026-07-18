package koready_backend.recommendation.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;
import koready_backend.recommendation.application.exception.RecommendationContextUnavailableException;
import koready_backend.recommendation.application.port.RecommendationDeckRepository;
import koready_backend.recommendation.domain.RecommendationScope;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class JdbcRecommendationDeckRepository implements RecommendationDeckRepository {

	private static final String CANDIDATE_SELECT = """
		SELECT
		    place.id,
		    COALESCE(requested.title, korean.title) AS title,
		    place.service_region_code,
		    COALESCE(
		        requested.address_text,
		        korean.address_text,
		        place.road_address,
		        place.address,
		        CASE WHEN ? = 'EN' THEN region.name_en ELSE region.name_ko END
		    ) AS location_text,
		    place.first_image_url,
		    COALESCE(requested.overview, korean.overview) AS overview,
		    place.data_quality_score
		FROM places place
		JOIN service_regions region ON region.code = place.service_region_code
		LEFT JOIN place_localizations requested
		    ON requested.place_id = place.id AND requested.language = ?
		LEFT JOIN place_localizations korean
		    ON korean.place_id = place.id AND korean.language = 'KO'
		WHERE place.active = TRUE
		  AND place.show_flag = TRUE
		  AND COALESCE(requested.title, korean.title) IS NOT NULL
		  AND NOT EXISTS (
		      SELECT 1
		      FROM user_place_recommendation_states state
		      WHERE state.user_id = ?
		        AND state.place_id = place.id
		        AND state.suppress_until > ?
		  )
		ORDER BY
		    CASE
		        WHEN ? = 'NEARBY' AND place.service_region_code <> ? THEN 1
		        ELSE 0
		    END ASC,
		    CASE WHEN EXISTS (
		        SELECT 1
		        FROM user_travel_styles user_style
		        JOIN place_style_mappings place_style
		          ON place_style.travel_style = user_style.travel_style
		         AND place_style.place_id = place.id
		        WHERE user_style.user_id = ?
		    ) THEN 0 ELSE 1 END ASC,
		    place.data_quality_score DESC,
		    place.id DESC
		LIMIT ?
		""";

	private static final String ITEM_SELECT = """
		SELECT
		    place_id,
		    title,
		    location_text,
		    image_url,
		    short_description,
		    service_region_code,
		    travel_style,
		    tags_json,
		    match_rank,
		    travel_style_matched,
		    preference_tag_matched,
		    matched_tag_codes_json
		FROM recommendation_deck_items
		WHERE deck_id = ?
		  AND display_order BETWEEN ? AND ?
		  %s
		ORDER BY display_order ASC
		""";

	private final JdbcTemplate jdbcTemplate;
	private final NamedParameterJdbcTemplate namedJdbcTemplate;
	private final JsonMapper jsonMapper;

	public JdbcRecommendationDeckRepository(
		JdbcTemplate jdbcTemplate,
		JsonMapper jsonMapper
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
		this.jsonMapper = jsonMapper;
	}

	@Override
	public Optional<UserRecommendationContext> findUserContext(
		String userPublicId,
		Long requestedLocationId
	) {
		List<UserRow> users = jdbcTemplate.query(
			"""
			SELECT id, public_id, default_location_id
			FROM users
			WHERE public_id = ? AND deleted_at IS NULL
			""",
			(rs, rowNumber) -> new UserRow(
				rs.getLong("id"),
				rs.getString("public_id"),
				nullableLong(rs, "default_location_id")),
			userPublicId);
		if (users.isEmpty()) {
			return Optional.empty();
		}
		UserRow user = users.getFirst();
		Long locationId = requestedLocationId != null
			? requestedLocationId
			: user.defaultLocationId();
		if (locationId == null) {
			return Optional.empty();
		}
		List<LocationRow> locations = jdbcTemplate.query(
			"""
			SELECT id, display_name, service_region_code
			FROM user_locations
			WHERE id = ? AND user_id = ? AND deleted_at IS NULL
			""",
			(rs, rowNumber) -> new LocationRow(
				rs.getLong("id"),
				rs.getString("display_name"),
				ServiceRegionCode.valueOf(rs.getString("service_region_code"))),
			locationId,
			user.id());
		if (locations.isEmpty()) {
			return Optional.empty();
		}
		List<TravelStyle> styles = jdbcTemplate.query(
			"""
			SELECT travel_style
			FROM user_travel_styles
			WHERE user_id = ?
			ORDER BY display_order ASC
			""",
			(rs, rowNumber) -> TravelStyle.valueOf(rs.getString("travel_style")),
			user.id());
		LocationRow location = locations.getFirst();
		return Optional.of(new UserRecommendationContext(
			user.id(),
			user.publicId(),
			location.id(),
			location.displayName(),
			location.serviceRegionCode(),
			List.copyOf(styles)));
	}

	@Override
	public List<RecommendationCandidate> findEligibleCandidates(
		long userId,
		Instant now,
		PlaceLanguage language,
		RecommendationScope scope,
		ServiceRegionCode originServiceRegionCode,
		int limit
	) {
		List<CandidateRow> rows = jdbcTemplate.query(
			CANDIDATE_SELECT,
			this::candidateRow,
			language.name(),
			language.name(),
			userId,
			Timestamp.from(now),
			scope.name(),
			originServiceRegionCode.name(),
			userId,
			limit);
		if (rows.isEmpty()) {
			return List.of();
		}
		List<Long> placeIds = rows.stream().map(CandidateRow::placeId).toList();
		Map<Long, List<TravelStyle>> styles = styles(placeIds);
		return rows.stream()
			.map(row -> new RecommendationCandidate(
				row.placeId(),
				row.title(),
				row.serviceRegionCode(),
				row.locationText(),
				row.imageUrl(),
				row.overview(),
				styles.getOrDefault(row.placeId(), List.of()),
				row.qualityScore()))
			.toList();
	}

	@Override
	@Transactional
	public StoredDeckPage createDeck(CreateDeckPlan plan) {
		List<Long> lockedUsers = jdbcTemplate.query(
			"""
			SELECT id FROM users
			WHERE id = ? AND public_id = ? AND deleted_at IS NULL
			FOR UPDATE
			""",
			(rs, rowNumber) -> rs.getLong("id"),
			plan.userId(),
			plan.userPublicId());
		if (lockedUsers.isEmpty()) {
			throw new RecommendationContextUnavailableException();
		}
		long deckId = insertDeck(plan);
		insertItems(deckId, plan.items());
		insertPages(deckId, plan.pages());
		return loadAndServePage(
			plan.userPublicId(), plan.deckPublicId(), null, plan.createdAt())
			.orElseThrow();
	}

	@Override
	@Transactional
	public Optional<StoredDeckPage> findPage(
		String userPublicId,
		String deckPublicId,
		String cursor,
		Instant now
	) {
		return loadAndServePage(userPublicId, deckPublicId, cursor, now);
	}

	private Optional<StoredDeckPage> loadAndServePage(
		String userPublicId,
		String deckPublicId,
		String cursor,
		Instant now
	) {
		List<Long> owners = jdbcTemplate.query(
			"""
			SELECT id
			FROM users
			WHERE public_id = ? AND deleted_at IS NULL
			FOR UPDATE
			""",
			(rs, rowNumber) -> rs.getLong("id"),
			userPublicId);
		if (owners.isEmpty()) {
			return Optional.empty();
		}
		long ownerId = owners.getFirst();
		String cursorClause = cursor == null
			? "page.page_number = 1"
			: "page.cursor_key = ?";
		String sql = """
			SELECT
			    deck.id AS deck_id,
			    deck.user_id,
			    deck.public_id,
			    deck.scope,
			    deck.origin_location_id,
			    deck.origin_display_name,
			    deck.origin_service_region_code,
			    deck.suppression_policy_version,
			    deck.suppression_days,
			    page.id AS page_id,
			    page.page_number,
			    page.start_order,
			    page.end_order,
			    page.served_at
			FROM recommendation_decks deck
			JOIN recommendation_deck_pages page ON page.deck_id = deck.id
			WHERE deck.public_id = ?
			  AND deck.user_id = ?
			  AND deck.expires_at > ?
			  AND %s
			FOR UPDATE
			""".formatted(cursorClause);
		Object[] arguments = cursor == null
			? new Object[] {deckPublicId, ownerId, Timestamp.from(now)}
			: new Object[] {deckPublicId, ownerId, Timestamp.from(now), cursor};
		List<PageRow> pages = jdbcTemplate.query(sql, this::pageRow, arguments);
		if (pages.isEmpty()) {
			return Optional.empty();
		}
		PageRow page = pages.getFirst();
		List<CardSnapshot> storedCards = page.endOrder() < page.startOrder()
			? List.of()
			: jdbcTemplate.query(
				ITEM_SELECT.formatted(page.servedAt() == null
					? ""
					: "AND served_at IS NOT NULL"),
				this::cardSnapshot,
				page.deckId(),
				page.startOrder(),
				page.endOrder());
		List<CardSnapshot> cards = storedCards;
		if (page.servedAt() == null) {
			cards = withoutActiveSuppression(page.userId(), storedCards, now);
			recordServed(page, cards, now);
		}
		String nextCursor = jdbcTemplate.query(
			"""
			SELECT cursor_key
			FROM recommendation_deck_pages
			WHERE deck_id = ? AND page_number = ?
			""",
			(rs, rowNumber) -> rs.getString("cursor_key"),
			page.deckId(),
			page.pageNumber() + 1)
			.stream()
			.findFirst()
			.orElse(null);
		return Optional.of(new StoredDeckPage(
			page.deckPublicId(),
			page.scope(),
			page.originLocationId(),
			page.originDisplayName(),
			page.originServiceRegionCode(),
			cards,
			nextCursor,
			nextCursor != null,
			page.suppressionPolicyVersion(),
			page.suppressionDays()));
	}

	private long insertDeck(CreateDeckPlan plan) {
		GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement(
				"""
				INSERT INTO recommendation_decks
				    (public_id, user_id, scope, origin_location_id,
				     origin_display_name, origin_service_region_code, language,
				     seed, cursor_version, suppression_policy_version,
				     suppression_days, page_size, created_at, expires_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				Statement.RETURN_GENERATED_KEYS);
			statement.setString(1, plan.deckPublicId());
			statement.setLong(2, plan.userId());
			statement.setString(3, plan.scope().name());
			statement.setLong(4, plan.originLocationId());
			statement.setString(5, plan.originDisplayName());
			statement.setString(6, plan.originServiceRegionCode().name());
			statement.setString(7, plan.language().name());
			statement.setString(8, plan.seed());
			statement.setInt(9, plan.cursorVersion());
			statement.setString(10, plan.suppressionPolicyVersion());
			statement.setInt(11, plan.suppressionDays());
			statement.setInt(12, plan.pageSize());
			statement.setTimestamp(13, Timestamp.from(plan.createdAt()));
			statement.setTimestamp(14, Timestamp.from(plan.expiresAt()));
			return statement;
		}, keyHolder);
		Number key = keyHolder.getKey();
		if (key == null) {
			throw new IllegalStateException("Recommendation deck key was not generated");
		}
		return key.longValue();
	}

	private void insertItems(long deckId, List<CardSnapshot> items) {
		for (int index = 0; index < items.size(); index++) {
			CardSnapshot item = items.get(index);
			jdbcTemplate.update(
				"""
				INSERT INTO recommendation_deck_items
				    (deck_id, place_id, display_order, title, location_text,
				     image_url, short_description, service_region_code, travel_style,
				     tags_json, match_rank, travel_style_matched,
				     preference_tag_matched, matched_tag_codes_json)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				deckId,
				item.placeId(),
				index + 1,
				item.title(),
				item.locationText(),
				item.imageUrl(),
				item.shortDescription(),
				item.serviceRegionCode().name(),
				item.travelStyle() == null ? null : item.travelStyle().name(),
				json(item.tags()),
				item.matchRank(),
				item.travelStyleMatched(),
				item.preferenceTagMatched(),
				json(item.matchedTagCodes()));
		}
	}

	private void insertPages(long deckId, List<PagePlan> pages) {
		for (PagePlan page : pages) {
			jdbcTemplate.update(
				"""
				INSERT INTO recommendation_deck_pages
				    (deck_id, page_number, cursor_key, start_order, end_order)
				VALUES (?, ?, ?, ?, ?)
				""",
				deckId,
				page.pageNumber(),
				page.cursor(),
				page.startOrder(),
				page.endOrder());
		}
	}

	private void recordServed(PageRow page, List<CardSnapshot> cards, Instant servedAt) {
		Timestamp servedTimestamp = Timestamp.from(servedAt);
		jdbcTemplate.update(
			"UPDATE recommendation_deck_pages SET served_at = ? WHERE id = ? AND served_at IS NULL",
			servedTimestamp,
			page.pageId());
		Instant suppressUntil = servedAt.plus(Duration.ofDays(page.suppressionDays()));
		for (CardSnapshot card : cards) {
			jdbcTemplate.update(
				"""
				UPDATE recommendation_deck_items
				SET served_at = ?
				WHERE deck_id = ? AND place_id = ? AND served_at IS NULL
				""",
				servedTimestamp,
				page.deckId(),
				card.placeId());
			jdbcTemplate.update(
				"""
				INSERT INTO user_place_events
				    (public_id, user_id, place_id, event_type, deck_id,
				     policy_version, suppression_days, occurred_at)
				VALUES (?, ?, ?, 'CARD_SERVED', ?, ?, ?, ?)
				""",
				"recevt_" + UUID.randomUUID(),
				page.userId(),
				card.placeId(),
				page.deckId(),
				page.suppressionPolicyVersion(),
				page.suppressionDays(),
				servedTimestamp);
			jdbcTemplate.update(
				"""
				INSERT INTO user_place_recommendation_states
				    (user_id, place_id, first_served_at, last_served_at,
				     served_count, last_deck_id, suppress_until,
				     suppression_policy_version, last_suppression_days,
				     last_event_type)
				VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?, 'CARD_SERVED')
				ON DUPLICATE KEY UPDATE
				    last_served_at = VALUES(last_served_at),
				    served_count = served_count + 1,
				    last_deck_id = VALUES(last_deck_id),
				    suppress_until = VALUES(suppress_until),
				    suppression_policy_version = VALUES(suppression_policy_version),
				    last_suppression_days = VALUES(last_suppression_days),
				    last_event_type = 'CARD_SERVED'
				""",
				page.userId(),
				card.placeId(),
				servedTimestamp,
				servedTimestamp,
				page.deckId(),
				Timestamp.from(suppressUntil),
				page.suppressionPolicyVersion(),
				page.suppressionDays());
		}
	}

	private List<CardSnapshot> withoutActiveSuppression(
		long userId,
		List<CardSnapshot> cards,
		Instant now
	) {
		if (cards.isEmpty()) {
			return List.of();
		}
		MapSqlParameterSource parameters = new MapSqlParameterSource()
			.addValue("userId", userId)
			.addValue("now", Timestamp.from(now))
			.addValue("placeIds", cards.stream().map(CardSnapshot::placeId).toList());
		Set<Long> suppressed = new HashSet<>(namedJdbcTemplate.query(
			"""
			SELECT place_id
			FROM user_place_recommendation_states
			WHERE user_id = :userId
			  AND suppress_until > :now
			  AND place_id IN (:placeIds)
			""",
			parameters,
			(rs, rowNumber) -> rs.getLong("place_id")));
		return cards.stream()
			.filter(card -> !suppressed.contains(card.placeId()))
			.toList();
	}

	private Map<Long, List<TravelStyle>> styles(List<Long> placeIds) {
		MapSqlParameterSource parameters = new MapSqlParameterSource("placeIds", placeIds);
		Map<Long, List<TravelStyle>> result = new HashMap<>();
		List<StyleRow> rows = namedJdbcTemplate.query(
			"""
			SELECT place_id, travel_style
			FROM place_style_mappings
			WHERE place_id IN (:placeIds)
			ORDER BY place_id ASC, confidence DESC, travel_style ASC
			""",
			parameters,
			(rs, rowNumber) -> new StyleRow(
				rs.getLong("place_id"),
				TravelStyle.valueOf(rs.getString("travel_style"))));
		for (StyleRow row : rows) {
			result.computeIfAbsent(row.placeId(), ignored -> new ArrayList<>())
				.add(row.travelStyle());
		}
		Map<Long, List<TravelStyle>> immutable = new LinkedHashMap<>();
		for (Long placeId : placeIds) {
			immutable.put(placeId, List.copyOf(result.getOrDefault(placeId, List.of())));
		}
		return immutable;
	}

	private CandidateRow candidateRow(ResultSet rs, int rowNumber) throws SQLException {
		return new CandidateRow(
			rs.getLong("id"),
			rs.getString("title"),
			ServiceRegionCode.valueOf(rs.getString("service_region_code")),
			rs.getString("location_text"),
			rs.getString("first_image_url"),
			rs.getString("overview"),
			rs.getBigDecimal("data_quality_score"));
	}

	private PageRow pageRow(ResultSet rs, int rowNumber) throws SQLException {
		Timestamp servedAt = rs.getTimestamp("served_at");
		return new PageRow(
			rs.getLong("deck_id"),
			rs.getLong("user_id"),
			rs.getString("public_id"),
			RecommendationScope.valueOf(rs.getString("scope")),
			rs.getLong("origin_location_id"),
			rs.getString("origin_display_name"),
			ServiceRegionCode.valueOf(rs.getString("origin_service_region_code")),
			rs.getString("suppression_policy_version"),
			rs.getInt("suppression_days"),
			rs.getLong("page_id"),
			rs.getInt("page_number"),
			rs.getInt("start_order"),
			rs.getInt("end_order"),
			servedAt == null ? null : servedAt.toInstant());
	}

	private CardSnapshot cardSnapshot(ResultSet rs, int rowNumber) throws SQLException {
		String style = rs.getString("travel_style");
		return new CardSnapshot(
			rs.getLong("place_id"),
			rs.getString("title"),
			rs.getString("location_text"),
			rs.getString("image_url"),
			rs.getString("short_description"),
			ServiceRegionCode.valueOf(rs.getString("service_region_code")),
			style == null ? null : TravelStyle.valueOf(style),
			strings(rs.getString("tags_json")),
			rs.getInt("match_rank"),
			rs.getBoolean("travel_style_matched"),
			rs.getBoolean("preference_tag_matched"),
			strings(rs.getString("matched_tag_codes_json")));
	}

	private String json(List<String> values) {
		try {
			return jsonMapper.writeValueAsString(values);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Recommendation snapshot JSON failed", exception);
		}
	}

	private List<String> strings(String value) {
		try {
			return List.of(jsonMapper.readValue(value, String[].class));
		} catch (JacksonException exception) {
			throw new IllegalStateException("Recommendation snapshot JSON failed", exception);
		}
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private record UserRow(long id, String publicId, Long defaultLocationId) {
	}

	private record LocationRow(
		long id,
		String displayName,
		ServiceRegionCode serviceRegionCode
	) {
	}

	private record CandidateRow(
		long placeId,
		String title,
		ServiceRegionCode serviceRegionCode,
		String locationText,
		String imageUrl,
		String overview,
		java.math.BigDecimal qualityScore
	) {
	}

	private record StyleRow(long placeId, TravelStyle travelStyle) {
	}

	private record PageRow(
		long deckId,
		long userId,
		String deckPublicId,
		RecommendationScope scope,
		long originLocationId,
		String originDisplayName,
		ServiceRegionCode originServiceRegionCode,
		String suppressionPolicyVersion,
		int suppressionDays,
		long pageId,
		int pageNumber,
		int startOrder,
		int endOrder,
		Instant servedAt
	) {
	}
}
