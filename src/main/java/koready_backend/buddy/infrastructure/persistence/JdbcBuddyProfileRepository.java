package koready_backend.buddy.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import koready_backend.buddy.application.port.BuddyProfileRepository;
import koready_backend.buddy.domain.BuddyProfileDraft;
import koready_backend.buddy.domain.BuddySocialLink;
import koready_backend.buddy.domain.BuddyStyle;
import koready_backend.buddy.domain.KoreanLevel;
import koready_backend.buddy.domain.SocialLinkType;
import koready_backend.place.domain.PlaceLanguage;

@Repository
public class JdbcBuddyProfileRepository implements BuddyProfileRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcBuddyProfileRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<Long> findActiveUserId(String publicId) {
		return findActiveUserId(publicId, false);
	}

	@Override
	public Optional<Long> findActiveUserIdForUpdate(String publicId) {
		return findActiveUserId(publicId, true);
	}

	@Override
	public Optional<BuddyProfileRecord> findByUserId(long userId) {
		List<ProfileRow> rows = jdbcTemplate.query(
			"""
			SELECT id, user_id, profile_image_url, nickname, nationality,
			       korean_level, bio, profile_public, sns_public, allows_messages,
			       created_at, updated_at
			FROM buddy_profiles
			WHERE user_id = ?
			""",
			this::mapProfile,
			userId);
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		ProfileRow row = rows.getFirst();
		BuddyProfileDraft profile = new BuddyProfileDraft(
			row.profileImageUrl(),
			row.nickname(),
			row.nationality(),
			languages(row.id()),
			row.koreanLevel(),
			row.bio(),
			styles(row.id()),
			socialLinks(row.id()),
			row.profilePublic(),
			row.snsPublic(),
			row.allowsMessages());
		return Optional.of(new BuddyProfileRecord(
			row.id(), row.userId(), profile, row.createdAt(), row.updatedAt()));
	}

	@Override
	public BuddyProfileRecord save(
		long userId,
		BuddyProfileDraft profile,
		Instant updatedAt
	) {
		Timestamp timestamp = Timestamp.from(updatedAt);
		jdbcTemplate.update(
			"""
			INSERT INTO buddy_profiles
			    (user_id, profile_image_url, nickname, nationality, korean_level, bio,
			     profile_public, sns_public, allows_messages, created_at, updated_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON DUPLICATE KEY UPDATE
			    profile_image_url = VALUES(profile_image_url),
			    nickname = VALUES(nickname),
			    nationality = VALUES(nationality),
			    korean_level = VALUES(korean_level),
			    bio = VALUES(bio),
			    profile_public = VALUES(profile_public),
			    sns_public = VALUES(sns_public),
			    allows_messages = VALUES(allows_messages),
			    updated_at = VALUES(updated_at)
			""",
			userId,
			profile.profileImageUrl(),
			profile.nickname(),
			profile.nationality(),
			profile.koreanLevel().name(),
			profile.bio(),
			profile.profilePublic(),
			profile.snsPublic(),
			profile.allowsMessages(),
			timestamp,
			timestamp);

		long profileId = jdbcTemplate.queryForObject(
			"SELECT id FROM buddy_profiles WHERE user_id = ?", Long.class, userId);
		replaceLanguages(profileId, profile.availableLanguages());
		replaceStyles(profileId, profile.buddyStyles());
		replaceSocialLinks(profileId, profile.socialLinks());
		return findByUserId(userId).orElseThrow();
	}

	private Optional<Long> findActiveUserId(String publicId, boolean forUpdate) {
		String sql = "SELECT id FROM users WHERE public_id = ? AND deleted_at IS NULL"
			+ (forUpdate ? " FOR UPDATE" : "");
		return jdbcTemplate.query(sql, (resultSet, rowNumber) -> resultSet.getLong("id"),
			publicId).stream().findFirst();
	}

	private List<PlaceLanguage> languages(long profileId) {
		return jdbcTemplate.query(
			"""
			SELECT language_code
			FROM buddy_profile_languages
			WHERE profile_id = ?
			ORDER BY display_order
			""",
			(resultSet, rowNumber) ->
				PlaceLanguage.valueOf(resultSet.getString("language_code")),
			profileId);
	}

	private List<BuddyStyle> styles(long profileId) {
		return jdbcTemplate.query(
			"""
			SELECT buddy_style
			FROM buddy_profile_styles
			WHERE profile_id = ?
			ORDER BY display_order
			""",
			(resultSet, rowNumber) ->
				BuddyStyle.valueOf(resultSet.getString("buddy_style")),
			profileId);
	}

	private List<BuddySocialLink> socialLinks(long profileId) {
		return jdbcTemplate.query(
			"""
			SELECT link_type, link_value
			FROM buddy_social_links
			WHERE profile_id = ?
			ORDER BY display_order
			""",
			(resultSet, rowNumber) -> new BuddySocialLink(
				SocialLinkType.valueOf(resultSet.getString("link_type")),
				resultSet.getString("link_value")),
			profileId);
	}

	private void replaceLanguages(long profileId, List<PlaceLanguage> languages) {
		jdbcTemplate.update(
			"DELETE FROM buddy_profile_languages WHERE profile_id = ?", profileId);
		if (languages.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate(
			"""
			INSERT INTO buddy_profile_languages (profile_id, language_code, display_order)
			VALUES (?, ?, ?)
			""",
			indexed(languages.stream().map(Enum::name).toList(), profileId));
	}

	private void replaceStyles(long profileId, List<BuddyStyle> styles) {
		jdbcTemplate.update(
			"DELETE FROM buddy_profile_styles WHERE profile_id = ?", profileId);
		if (styles.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate(
			"""
			INSERT INTO buddy_profile_styles (profile_id, buddy_style, display_order)
			VALUES (?, ?, ?)
			""",
			indexed(styles.stream().map(Enum::name).toList(), profileId));
	}

	private void replaceSocialLinks(long profileId, List<BuddySocialLink> links) {
		jdbcTemplate.update("DELETE FROM buddy_social_links WHERE profile_id = ?", profileId);
		if (links.isEmpty()) {
			return;
		}
		List<Object[]> rows = java.util.stream.IntStream.range(0, links.size())
			.mapToObj(index -> new Object[] {
				profileId,
				links.get(index).type().name(),
				links.get(index).value(),
				index + 1
			})
			.toList();
		jdbcTemplate.batchUpdate(
			"""
			INSERT INTO buddy_social_links
			    (profile_id, link_type, link_value, display_order)
			VALUES (?, ?, ?, ?)
			""",
			rows);
	}

	private static List<Object[]> indexed(List<String> values, long profileId) {
		return java.util.stream.IntStream.range(0, values.size())
			.mapToObj(index -> new Object[] {profileId, values.get(index), index + 1})
			.toList();
	}

	private ProfileRow mapProfile(ResultSet resultSet, int rowNumber) throws SQLException {
		return new ProfileRow(
			resultSet.getLong("id"),
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

	private record ProfileRow(
		long id,
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
