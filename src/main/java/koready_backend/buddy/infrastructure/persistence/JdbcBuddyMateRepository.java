package koready_backend.buddy.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import koready_backend.buddy.application.port.BuddyMateRepository;
import koready_backend.buddy.application.port.BuddyProfileRepository.BuddyProfileRecord;
import koready_backend.buddy.domain.BuddyProfileDraft;
import koready_backend.buddy.domain.BuddySocialLink;
import koready_backend.buddy.domain.BuddyStyle;
import koready_backend.buddy.domain.KoreanLevel;
import koready_backend.buddy.domain.SocialLinkType;
import koready_backend.place.domain.PlaceLanguage;

@Repository
public class JdbcBuddyMateRepository implements BuddyMateRepository {

	private static final String MATE_SELECT = """
		SELECT
		    saved.id AS saved_place_id,
		    saved.saved_at,
		    profile.id AS profile_id,
		    profile.user_id,
		    profile.profile_image_url,
		    profile.nickname,
		    profile.nationality,
		    profile.korean_level,
		    profile.bio,
		    profile.profile_public,
		    profile.sns_public,
		    profile.allows_messages,
		    profile.created_at,
		    profile.updated_at
		FROM user_saved_places saved
		JOIN users owner ON owner.id = saved.user_id
		JOIN buddy_profiles profile ON profile.user_id = owner.id
		WHERE saved.place_id = :placeId
		  AND saved.deleted_at IS NULL
		  AND owner.deleted_at IS NULL
		  AND profile.profile_public = TRUE
		  AND profile.user_id <> :requesterUserId
		  AND EXISTS (
		      SELECT 1
		      FROM buddy_profile_languages language
		      WHERE language.profile_id = profile.id
		  )
		  AND NOT EXISTS (
		      SELECT 1
		      FROM buddy_blocks blocked_by_requester
		      WHERE blocked_by_requester.blocker_user_id = :requesterUserId
		        AND blocked_by_requester.blocked_user_id = profile.user_id
		  )
		  AND NOT EXISTS (
		      SELECT 1
		      FROM buddy_blocks blocked_requester
		      WHERE blocked_requester.blocker_user_id = profile.user_id
		        AND blocked_requester.blocked_user_id = :requesterUserId
		  )
		""";

	private final JdbcTemplate jdbcTemplate;
	private final NamedParameterJdbcTemplate namedJdbcTemplate;

	public JdbcBuddyMateRepository(
		JdbcTemplate jdbcTemplate,
		NamedParameterJdbcTemplate namedJdbcTemplate
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.namedJdbcTemplate = namedJdbcTemplate;
	}

	@Override
	public boolean existsVisiblePlace(long placeId) {
		Boolean exists = jdbcTemplate.queryForObject(
			"""
			SELECT EXISTS(
			    SELECT 1
			    FROM places
			    WHERE id = ? AND active = TRUE AND show_flag = TRUE
			)
			""",
			Boolean.class,
			placeId);
		return Boolean.TRUE.equals(exists);
	}

	@Override
	public List<MateRow> findAll(MateQuery query) {
		MapSqlParameterSource parameters = new MapSqlParameterSource()
			.addValue("requesterUserId", query.requesterUserId())
			.addValue("placeId", query.placeId())
			.addValue("limit", query.limit());
		StringBuilder sql = new StringBuilder(MATE_SELECT);
		if (query.cursor() != null) {
			parameters
				.addValue("cursorSavedAt", Timestamp.from(query.cursor().savedAt()))
				.addValue("cursorSavedPlaceId", query.cursor().savedPlaceId());
			sql.append("""
				  AND (
				      saved.saved_at < :cursorSavedAt
				      OR (
				          saved.saved_at = :cursorSavedAt
				          AND saved.id < :cursorSavedPlaceId
				      )
				  )
				""");
		}
		sql.append("ORDER BY saved.saved_at DESC, saved.id DESC\nLIMIT :limit");
		List<ProfileBaseRow> rows = namedJdbcTemplate.query(
			sql.toString(), parameters, this::mapProfileBase);
		return attachAssociations(rows);
	}

	private List<MateRow> attachAssociations(List<ProfileBaseRow> rows) {
		if (rows.isEmpty()) {
			return List.of();
		}
		List<Long> profileIds = rows.stream().map(ProfileBaseRow::profileId).toList();
		Map<Long, List<PlaceLanguage>> languages = languages(profileIds);
		Map<Long, List<BuddyStyle>> styles = styles(profileIds);
		Map<Long, List<BuddySocialLink>> socialLinks = socialLinks(profileIds);

		return rows.stream().map(row -> {
			BuddyProfileDraft profile = new BuddyProfileDraft(
				row.profileImageUrl(),
				row.nickname(),
				row.nationality(),
				languages.getOrDefault(row.profileId(), List.of()),
				row.koreanLevel(),
				row.bio(),
				styles.getOrDefault(row.profileId(), List.of()),
				socialLinks.getOrDefault(row.profileId(), List.of()),
				row.profilePublic(),
				row.snsPublic(),
				row.allowsMessages());
			BuddyProfileRecord record = new BuddyProfileRecord(
				row.profileId(),
				row.userId(),
				profile,
				row.createdAt(),
				row.updatedAt());
			return new MateRow(row.savedPlaceId(), row.savedAt(), record);
		}).toList();
	}

	private Map<Long, List<PlaceLanguage>> languages(List<Long> profileIds) {
		Map<Long, List<PlaceLanguage>> values = new HashMap<>();
		namedJdbcTemplate.query(
			"""
			SELECT profile_id, language_code
			FROM buddy_profile_languages
			WHERE profile_id IN (:profileIds)
			ORDER BY profile_id, display_order
			""",
			ids(profileIds),
			(RowCallbackHandler) resultSet -> values
				.computeIfAbsent(resultSet.getLong("profile_id"), ignored -> new ArrayList<>())
				.add(PlaceLanguage.valueOf(resultSet.getString("language_code"))));
		return values;
	}

	private Map<Long, List<BuddyStyle>> styles(List<Long> profileIds) {
		Map<Long, List<BuddyStyle>> values = new HashMap<>();
		namedJdbcTemplate.query(
			"""
			SELECT profile_id, buddy_style
			FROM buddy_profile_styles
			WHERE profile_id IN (:profileIds)
			ORDER BY profile_id, display_order
			""",
			ids(profileIds),
			(RowCallbackHandler) resultSet -> values
				.computeIfAbsent(resultSet.getLong("profile_id"), ignored -> new ArrayList<>())
				.add(BuddyStyle.valueOf(resultSet.getString("buddy_style"))));
		return values;
	}

	private Map<Long, List<BuddySocialLink>> socialLinks(List<Long> profileIds) {
		Map<Long, List<BuddySocialLink>> values = new HashMap<>();
		namedJdbcTemplate.query(
			"""
			SELECT profile_id, link_type, link_value
			FROM buddy_social_links
			WHERE profile_id IN (:profileIds)
			ORDER BY profile_id, display_order
			""",
			ids(profileIds),
			(RowCallbackHandler) resultSet -> values
				.computeIfAbsent(resultSet.getLong("profile_id"), ignored -> new ArrayList<>())
				.add(new BuddySocialLink(
					SocialLinkType.valueOf(resultSet.getString("link_type")),
					resultSet.getString("link_value"))));
		return values;
	}

	private static MapSqlParameterSource ids(List<Long> profileIds) {
		return new MapSqlParameterSource().addValue("profileIds", profileIds);
	}

	private ProfileBaseRow mapProfileBase(ResultSet resultSet, int rowNumber)
		throws SQLException {
		return new ProfileBaseRow(
			resultSet.getLong("saved_place_id"),
			resultSet.getTimestamp("saved_at").toInstant(),
			resultSet.getLong("profile_id"),
			resultSet.getLong("user_id"),
			resultSet.getString("profile_image_url"),
			resultSet.getString("nickname"),
			resultSet.getString("nationality"),
			KoreanLevel.valueOf(resultSet.getString("korean_level")),
			resultSet.getString("bio"),
			resultSet.getBoolean("profile_public"),
			resultSet.getBoolean("sns_public"),
			resultSet.getBoolean("allows_messages"),
			resultSet.getTimestamp("created_at").toInstant(),
			resultSet.getTimestamp("updated_at").toInstant());
	}

	private record ProfileBaseRow(
		long savedPlaceId,
		Instant savedAt,
		long profileId,
		long userId,
		String profileImageUrl,
		String nickname,
		String nationality,
		KoreanLevel koreanLevel,
		String bio,
		boolean profilePublic,
		boolean snsPublic,
		boolean allowsMessages,
		Instant createdAt,
		Instant updatedAt
	) {
	}
}
