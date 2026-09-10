package koready_backend.terms.infrastructure.persistence;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import koready_backend.terms.application.port.AdminTermsRepository;
import koready_backend.terms.application.port.AdminTermsRepository.TermTranslation;
import koready_backend.terms.domain.TermContentFormat;
import koready_backend.place.domain.PlaceLanguage;

@Repository
public class JdbcAdminTermsRepository implements AdminTermsRepository {
	private final JdbcTemplate jdbc;
	public JdbcAdminTermsRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

	@Override
	public List<TermDefinition> findAll() {
		var definitions = new LinkedHashMap<Long, DefinitionBuilder>();
		jdbc.query("""
			SELECT d.id term_id, d.code, d.display_order, d.enabled,
			 v.id version_id, v.version_label, v.title, v.content_url, v.content_body,
			 v.content_format, v.required,
			 v.effective_at, v.published_at, v.withdrawn_at
			FROM term_definitions d LEFT JOIN term_versions v ON v.term_id=d.id
			ORDER BY d.display_order, d.id, v.created_at DESC, v.id DESC
			""", rs -> {
			var definition = definitions.computeIfAbsent(rs.getLong("term_id"), id -> new DefinitionBuilder(
				id, string(rs, "code"), integer(rs, "display_order"), bool(rs, "enabled")));
			Long versionId = (Long) rs.getObject("version_id");
			if (versionId != null) definition.versions.add(version(rs));
		});
		return definitions.values().stream().map(DefinitionBuilder::build).toList();
	}

	@Override
	public TermDefinition createDefinition(String code, int displayOrder, boolean enabled, Instant now) {
		var key = new GeneratedKeyHolder();
		jdbc.update(connection -> {
			var statement = connection.prepareStatement("INSERT INTO term_definitions(code, display_order, enabled, created_at, updated_at) VALUES(?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
			statement.setString(1, code); statement.setInt(2, displayOrder); statement.setBoolean(3, enabled);
			statement.setTimestamp(4, Timestamp.from(now)); statement.setTimestamp(5, Timestamp.from(now)); return statement;
		}, key);
		return new TermDefinition(key.getKey().longValue(), code, displayOrder, enabled, List.of());
	}

	@Override
	public Optional<TermDefinition> updateDefinition(long id, int displayOrder, boolean enabled, Instant now) {
		if (jdbc.update("UPDATE term_definitions SET display_order=?, enabled=?, updated_at=? WHERE id=?", displayOrder, enabled, Timestamp.from(now), id) == 0) return Optional.empty();
		return findAll().stream().filter(term -> term.id() == id).findFirst();
	}

	@Override
	public Optional<TermVersion> findVersion(long termId, long versionId) {
		return jdbc.query("SELECT id AS version_id, term_id, version_label, title, content_url, content_body, content_format, required, effective_at, published_at, withdrawn_at FROM term_versions WHERE term_id=? AND id=?",
			(rs, row) -> version(rs), termId, versionId).stream().findFirst();
	}

	@Override
	public Optional<TermVersion> createVersion(long termId, String version, String title, URI contentUrl,
		String content, TermContentFormat contentFormat, boolean required, Instant effectiveAt, Instant now) {
		if (jdbc.queryForObject("SELECT COUNT(*) FROM term_definitions WHERE id=?", Integer.class, termId) == 0) return Optional.empty();
		var key = new GeneratedKeyHolder();
		jdbc.update(connection -> {
			var statement = connection.prepareStatement("INSERT INTO term_versions(term_id, version_label, title, content_url, content_body, content_format, required, effective_at, created_at, updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
			statement.setLong(1, termId); statement.setString(2, version); statement.setString(3, title);
			statement.setString(4, contentUrl == null ? null : contentUrl.toString()); statement.setString(5, content);
			statement.setString(6, contentFormat == null ? null : contentFormat.name()); statement.setBoolean(7, required);
			statement.setTimestamp(8, Timestamp.from(effectiveAt)); statement.setTimestamp(9, Timestamp.from(now)); statement.setTimestamp(10, Timestamp.from(now)); return statement;
		}, key);
		return findVersion(termId, key.getKey().longValue());
	}

	@Override
	public Optional<TermVersion> updateDraft(long termId, long versionId, String version, String title,
		URI contentUrl, String content, TermContentFormat contentFormat, boolean required,
		Instant effectiveAt, Instant now) {
		int changed = jdbc.update("""
			UPDATE term_versions SET version_label=?, title=?, content_url=?, content_body=?, content_format=?,
			 required=?, effective_at=?, updated_at=?
			WHERE term_id=? AND id=? AND published_at IS NULL
			""", version, title, contentUrl == null ? null : contentUrl.toString(), content,
			contentFormat == null ? null : contentFormat.name(), required,
			Timestamp.from(effectiveAt), Timestamp.from(now), termId, versionId);
		return changed == 0 ? Optional.empty() : findVersion(termId, versionId);
	}

	@Override
	public Optional<TermVersion> publish(long termId, long versionId, Instant now) {
		int changed = jdbc.update("UPDATE term_versions SET published_at=?, updated_at=? WHERE term_id=? AND id=? AND published_at IS NULL AND (content_url IS NOT NULL OR (content_body IS NOT NULL AND content_format IS NOT NULL))",
			Timestamp.from(now), Timestamp.from(now), termId, versionId);
		return changed == 0 ? Optional.empty() : findVersion(termId, versionId);
	}

	@Override
	public Optional<TermVersion> withdraw(long termId, long versionId, Instant now) {
		int changed = jdbc.update("UPDATE term_versions SET withdrawn_at=?, updated_at=? WHERE term_id=? AND id=? AND published_at IS NOT NULL AND withdrawn_at IS NULL",
			Timestamp.from(now), Timestamp.from(now), termId, versionId);
		return changed == 0 ? Optional.empty() : findVersion(termId, versionId);
	}

	@Override
	public void replaceTranslations(long versionId, List<TermTranslation> translations, Instant now) {
		jdbc.update("DELETE FROM term_version_localizations WHERE term_version_id=?", versionId);
		for (TermTranslation value : translations) {
			jdbc.update("""
				INSERT INTO term_version_localizations
				 (term_version_id, language, title, content_url, content_body, content_format, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""", versionId, value.language().name(), value.title(),
				value.contentUrl() == null ? null : value.contentUrl().toString(), value.content(),
				value.contentFormat() == null ? null : value.contentFormat().name(),
				Timestamp.from(now), Timestamp.from(now));
		}
	}

	private TermVersion version(ResultSet rs) throws SQLException {
		String url = rs.getString("content_url");
		String format = rs.getString("content_format");
		long versionId = rs.getLong("version_id");
		return new TermVersion(rs.getLong("version_id"), rs.getLong("term_id"), rs.getString("version_label"),
			rs.getString("title"), url == null ? null : URI.create(url), rs.getString("content_body"),
			format == null ? null : TermContentFormat.valueOf(format), rs.getBoolean("required"),
			rs.getTimestamp("effective_at").toInstant(), instant(rs.getTimestamp("published_at")),
			instant(rs.getTimestamp("withdrawn_at")), translations(versionId));
	}

	private List<TermTranslation> translations(long versionId) {
		return jdbc.query("""
			SELECT language, title, content_url, content_body, content_format
			FROM term_version_localizations
			WHERE term_version_id=? ORDER BY language
			""", (rs, row) -> {
			String url = rs.getString("content_url");
			String format = rs.getString("content_format");
			return new TermTranslation(PlaceLanguage.valueOf(rs.getString("language")), rs.getString("title"),
				url == null ? null : URI.create(url), rs.getString("content_body"),
				format == null ? null : TermContentFormat.valueOf(format));
		}, versionId);
	}

	private static Instant instant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }
	private static String string(ResultSet rs, String column) { try { return rs.getString(column); } catch (SQLException e) { throw new IllegalStateException(e); } }
	private static int integer(ResultSet rs, String column) { try { return rs.getInt(column); } catch (SQLException e) { throw new IllegalStateException(e); } }
	private static boolean bool(ResultSet rs, String column) { try { return rs.getBoolean(column); } catch (SQLException e) { throw new IllegalStateException(e); } }

	private static final class DefinitionBuilder {
		private final long id; private final String code; private final int order; private final boolean enabled;
		private final List<TermVersion> versions = new ArrayList<>();
		private DefinitionBuilder(long id, String code, int order, boolean enabled) { this.id=id; this.code=code; this.order=order; this.enabled=enabled; }
		private TermDefinition build() { return new TermDefinition(id, code, order, enabled, List.copyOf(versions)); }
	}
}
