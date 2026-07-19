package koready_backend.evidence.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.evidence.application.port.EvidenceBundleRepository;
import koready_backend.evidence.application.port.EvidenceBundleRepository.AuditRecord;
import koready_backend.evidence.application.port.EvidenceBundleRepository.BundleRecord;
import koready_backend.evidence.application.port.EvidenceBundleRepository.CreateRecord;
import koready_backend.evidence.application.port.EvidenceBundleRepository.ExclusionRecord;
import koready_backend.evidence.application.port.EvidenceBundleRepository.ManifestFileRecord;
import koready_backend.evidence.domain.EvidenceBundleStatus;
import koready_backend.externalapi.domain.ExternalApiProvider;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class JdbcEvidenceBundleRepository implements EvidenceBundleRepository {

	private static final String SELECT = """
		SELECT id, bundle_id, name, status, from_at, to_at, providers_json, operations_json,
		       include_raw_snapshots, raw_sample_limit_per_operation, storage_key, file_name,
		       sha256, byte_size, call_count, raw_snapshot_count, created_by_subject,
		       created_at, started_at, finished_at, failure_reason
		FROM evidence_bundles
		""";

	private final JdbcTemplate jdbcTemplate;
	private final JsonMapper jsonMapper;

	public JdbcEvidenceBundleRepository(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.jsonMapper = jsonMapper;
	}

	@Override
	public BundleRecord create(CreateRecord record) {
		jdbcTemplate.update("""
			INSERT INTO evidence_bundles (
			    bundle_id, name, status, from_at, to_at, providers_json, operations_json,
			    include_raw_snapshots, raw_sample_limit_per_operation, created_by_subject, created_at
			) VALUES (?, ?, 'QUEUED', ?, ?, CAST(? AS JSON), CAST(? AS JSON), ?, ?, ?, ?)
			""",
			record.bundleId(), record.name(), timestamp(record.from()), timestamp(record.to()),
			json(record.providers().stream().map(Enum::name).toList()), json(record.operations()),
			record.includeRawSnapshots(), record.rawSampleLimitPerOperation(),
			record.createdBySubject(), timestamp(record.createdAt()));
		return findByBundleId(record.bundleId()).orElseThrow();
	}

	@Override
	public List<BundleRecord> findPage(Long beforeId, int limit) {
		String condition = beforeId == null ? "" : "WHERE id < ?";
		Object[] args = beforeId == null ? new Object[] {limit} : new Object[] {beforeId, limit};
		return jdbcTemplate.query(SELECT + " " + condition + " ORDER BY id DESC LIMIT ?", args,
			(resultSet, rowNumber) -> mapBundle(resultSet));
	}

	@Override
	public Optional<BundleRecord> findByBundleId(String bundleId) {
		List<BundleRecord> records = jdbcTemplate.query(
			SELECT + " WHERE bundle_id = ?", (resultSet, rowNumber) -> mapBundle(resultSet), bundleId);
		return records.stream().findFirst();
	}

	@Override
	@Transactional
	public Optional<BundleRecord> claimNextQueued(Instant startedAt) {
		List<Long> ids = jdbcTemplate.query("""
			SELECT id FROM evidence_bundles
			WHERE status = 'QUEUED'
			ORDER BY created_at ASC, id ASC
			LIMIT 1 FOR UPDATE
			""", (resultSet, rowNumber) -> resultSet.getLong("id"));
		if (ids.isEmpty()) {
			return Optional.empty();
		}
		long id = ids.getFirst();
		int updated = jdbcTemplate.update("""
			UPDATE evidence_bundles
			SET status = 'RUNNING', started_at = ?, failure_reason = NULL
			WHERE id = ? AND status = 'QUEUED'
			""", timestamp(startedAt), id);
		if (updated == 0) {
			return Optional.empty();
		}
		return jdbcTemplate.query(SELECT + " WHERE id = ?", (resultSet, rowNumber) -> mapBundle(resultSet), id)
			.stream().findFirst();
	}

	@Override
	@Transactional
	public void complete(EvidenceBundleRepository.CompletionRecord completion) {
		BundleRecord bundle = findByBundleId(completion.bundleId()).orElseThrow();
		int updated = jdbcTemplate.update("""
			UPDATE evidence_bundles
			SET status = 'COMPLETED', storage_key = ?, file_name = ?, sha256 = ?, byte_size = ?,
			    call_count = ?, raw_snapshot_count = ?, finished_at = ?, failure_reason = NULL
			WHERE id = ? AND status = 'RUNNING'
			""", completion.storageKey(), completion.fileName(), completion.sha256(), completion.byteSize(),
			completion.callCount(), completion.rawSnapshotCount(), timestamp(completion.finishedAt()), bundle.id());
		if (updated == 0) {
			throw new IllegalStateException("Evidence bundle completion state is invalid");
		}
		jdbcTemplate.update("DELETE FROM evidence_bundle_exclusions WHERE evidence_bundle_id = ?", bundle.id());
		jdbcTemplate.update("DELETE FROM evidence_bundle_manifest_files WHERE evidence_bundle_id = ?", bundle.id());
		for (var exclusion : completion.exclusions()) {
			jdbcTemplate.update("""
				INSERT INTO evidence_bundle_exclusions (evidence_bundle_id, provider, reason)
				VALUES (?, ?, ?)
				""", bundle.id(), exclusion.provider().name(), exclusion.reason());
		}
		for (var file : completion.manifestFiles()) {
			jdbcTemplate.update("""
				INSERT INTO evidence_bundle_manifest_files (evidence_bundle_id, path, sha256, byte_size)
				VALUES (?, ?, ?, ?)
				""", bundle.id(), file.path(), file.sha256(), file.byteSize());
		}
	}

	@Override
	@Transactional
	public void fail(String bundleId, String failureReason, Instant finishedAt) {
		jdbcTemplate.update("""
			UPDATE evidence_bundles
			SET status = 'FAILED', finished_at = ?, failure_reason = ?
			WHERE bundle_id = ? AND status = 'RUNNING'
			""", timestamp(finishedAt), failureReason, bundleId);
	}

	@Override
	public void recordAudit(AuditRecord audit) {
		jdbcTemplate.update("""
			INSERT INTO admin_audit_logs (
			    actor_subject, action, resource_type, resource_id, created_at, after_snapshot
			) VALUES (?, ?, 'EVIDENCE_BUNDLE', ?, ?, CAST(? AS JSON))
			""",
			audit.actorSubject(), audit.action(), audit.bundleId(), timestamp(audit.occurredAt()), json(audit.details()));
	}

	private BundleRecord mapBundle(ResultSet resultSet) throws SQLException {
		long id = resultSet.getLong("id");
		return new BundleRecord(
			id,
			resultSet.getString("bundle_id"),
			resultSet.getString("name"),
			EvidenceBundleStatus.valueOf(resultSet.getString("status")),
			instant(resultSet, "from_at"),
			instant(resultSet, "to_at"),
			providers(resultSet.getString("providers_json")),
			strings(resultSet.getString("operations_json")),
			resultSet.getBoolean("include_raw_snapshots"),
			resultSet.getInt("raw_sample_limit_per_operation"),
			resultSet.getString("storage_key"),
			resultSet.getString("file_name"),
			resultSet.getString("sha256"),
			nullableLong(resultSet, "byte_size"),
			nullableLong(resultSet, "call_count"),
			nullableLong(resultSet, "raw_snapshot_count"),
			resultSet.getString("created_by_subject"),
			instant(resultSet, "created_at"),
			instant(resultSet, "started_at"),
			instant(resultSet, "finished_at"),
			resultSet.getString("failure_reason"),
			exclusions(id),
			manifestFiles(id));
	}

	private List<ExclusionRecord> exclusions(long bundleId) {
		return jdbcTemplate.query("""
			SELECT provider, reason FROM evidence_bundle_exclusions
			WHERE evidence_bundle_id = ? ORDER BY id ASC
			""", (resultSet, rowNumber) -> new ExclusionRecord(
			ExternalApiProvider.valueOf(resultSet.getString("provider")), resultSet.getString("reason")), bundleId);
	}

	private List<ManifestFileRecord> manifestFiles(long bundleId) {
		return jdbcTemplate.query("""
			SELECT path, sha256, byte_size FROM evidence_bundle_manifest_files
			WHERE evidence_bundle_id = ? ORDER BY path ASC
			""", (resultSet, rowNumber) -> new ManifestFileRecord(
			resultSet.getString("path"), resultSet.getString("sha256"), resultSet.getLong("byte_size")), bundleId);
	}

	private List<ExternalApiProvider> providers(String value) {
		return strings(value).stream().map(ExternalApiProvider::valueOf).toList();
	}

	@SuppressWarnings("unchecked")
	private List<String> strings(String value) {
		try {
			return List.copyOf(jsonMapper.readValue(value, List.class));
		} catch (JacksonException exception) {
			throw new IllegalStateException("Evidence bundle JSON metadata could not be parsed", exception);
		}
	}

	private String json(Object value) {
		try {
			return jsonMapper.writeValueAsString(value);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Evidence bundle JSON metadata could not be serialized", exception);
		}
	}

	private static Timestamp timestamp(Instant value) {
		return value == null ? null : Timestamp.from(value);
	}

	private static Instant instant(ResultSet resultSet, String column) throws SQLException {
		Timestamp value = resultSet.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}

	private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
		long value = resultSet.getLong(column);
		return resultSet.wasNull() ? null : value;
	}
}
