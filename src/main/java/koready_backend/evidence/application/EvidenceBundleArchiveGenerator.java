package koready_backend.evidence.application;

import java.io.BufferedWriter;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import koready_backend.evidence.application.port.EvidenceBundleContentSource;
import koready_backend.evidence.application.port.EvidenceBundleContentSource.CallRow;
import koready_backend.evidence.application.port.EvidenceBundleContentSource.Selection;
import koready_backend.evidence.application.port.EvidenceBundleContentSource.SnapshotRow;
import koready_backend.evidence.application.port.EvidenceBundleRepository.BundleRecord;
import koready_backend.evidence.application.port.EvidenceBundleRepository.ExclusionRecord;
import koready_backend.evidence.application.port.EvidenceBundleRepository.ManifestFileRecord;
import koready_backend.evidence.application.port.EvidenceRawSnapshotReader;
import koready_backend.externalapi.domain.ExternalApiProvider;
import koready_backend.externalapi.domain.SnapshotRetentionClass;
import koready_backend.externalapi.domain.SnapshotStorageFormat;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class EvidenceBundleArchiveGenerator {

	private static final int PAGE_SIZE = 500;
	private static final int COPY_BUFFER_SIZE = 16 * 1024;
	private static final DateTimeFormatter FILE_MONTH = DateTimeFormatter.ofPattern("yyyyMM")
		.withZone(ZoneId.of("Asia/Seoul"));

	private final EvidenceBundleContentSource contentSource;
	private final EvidenceRawSnapshotReader snapshotReader;
	private final JsonMapper jsonMapper;
	private final Clock clock;

	@Autowired
	public EvidenceBundleArchiveGenerator(
		EvidenceBundleContentSource contentSource,
		EvidenceRawSnapshotReader snapshotReader,
		JsonMapper jsonMapper
	) {
		this(contentSource, snapshotReader, jsonMapper, Clock.systemUTC());
	}

	EvidenceBundleArchiveGenerator(
		EvidenceBundleContentSource contentSource,
		EvidenceRawSnapshotReader snapshotReader,
		JsonMapper jsonMapper,
		Clock clock
	) {
		this.contentSource = contentSource;
		this.snapshotReader = snapshotReader;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	public GeneratedArchive generate(BundleRecord bundle) {
		Path archive = null;
		try {
			archive = Files.createTempFile("koready-evidence-", ".zip");
			GenerationState state = new GenerationState(bundle);
			try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
				state.files.add(writeCalls(zip, state));
				state.files.add(writeBatchJobs(zip, state));
				state.files.add(writeJson(zip, "sync_cursors.json", syncCursorDocument(state)));
				state.files.add(writeJson(zip, "data_quality_summary.json", dataQualityDocument()));
				writeRawSnapshots(zip, state);
				state.files.add(writeJson(zip, "manifest.json", manifestDocument(state)));
				state.files.add(writeShaSums(zip, state.files));
			}
			return new GeneratedArchive(
				archive,
				"koready-openapi-evidence-" + FILE_MONTH.format(bundle.createdAt()) + ".zip",
				sha256(archive),
				Files.size(archive),
				state.callCount,
				state.rawSnapshotCount,
				List.copyOf(state.exclusions.values()),
				List.copyOf(state.files));
		} catch (IOException | RuntimeException exception) {
			if (archive != null) {
				try {
					Files.deleteIfExists(archive);
				} catch (IOException ignored) {
					// The system temporary-file cleaner is the final fallback.
				}
			}
			throw new IllegalStateException("Evidence bundle archive could not be generated", exception);
		}
	}

	private ManifestFileRecord writeCalls(ZipOutputStream zip, GenerationState state) throws IOException {
		return writeStreamingEntry(zip, "open_api_calls.csv", output -> {
			try (BufferedWriter writer = writer(output)) {
				writer.write("callLogId,provider,apiName,operation,requestStartedAt,responseReceivedAt,durationMs,success,httpStatus,externalResultCode,itemCount,responseBytes,rawSnapshotId\n");
				long afterId = 0;
				while (true) {
					List<CallRow> calls = contentSource.findCalls(state.selection, afterId, PAGE_SIZE);
					for (CallRow call : calls) {
						writer.write(csv(call.id(), call.provider(), call.apiName(), call.operation(),
							call.requestStartedAt(), call.responseReceivedAt(), call.durationMs(), call.success(),
							call.httpStatus(), call.externalResultCode(), call.itemCount(), call.responseBytes(),
							call.snapshot() == null ? null : call.snapshot().snapshotId()));
						writer.write('\n');
						state.callCount++;
						considerSnapshot(state, call);
						afterId = call.id();
					}
					if (calls.size() < PAGE_SIZE) {
						break;
					}
				}
				writer.flush();
			}
		});
	}

	private ManifestFileRecord writeBatchJobs(ZipOutputStream zip, GenerationState state) throws IOException {
		return writeStreamingEntry(zip, "batch_jobs.csv", output -> {
			try (BufferedWriter writer = writer(output)) {
				writer.write("jobId,jobType,status,startedAt,finishedAt,processedCount,successCount,failureCount,createdAt\n");
				long afterId = 0;
				while (true) {
					var jobs = contentSource.findBatchJobs(state.bundle.from(), state.bundle.to(), afterId, PAGE_SIZE);
					for (var job : jobs) {
						writer.write(csv(job.id(), job.jobType(), job.status(), job.startedAt(), job.finishedAt(),
							job.processedCount(), job.successCount(), job.failureCount(), job.createdAt()));
						writer.write('\n');
						afterId = job.id();
					}
					if (jobs.size() < PAGE_SIZE) {
						break;
					}
				}
				writer.flush();
			}
		});
	}

	private Map<String, Object> syncCursorDocument(GenerationState state) {
		return Map.of("items", contentSource.findSyncCursors(state.selection).stream()
			.map(this::syncCursor).toList());
	}

	private Map<String, Object> syncCursor(EvidenceBundleContentSource.SyncCursorRow cursor) {
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("cursorId", cursor.id());
		item.put("provider", cursor.provider().name());
		item.put("apiName", cursor.apiName());
		item.put("operation", cursor.operation());
		item.put("cursorType", cursor.cursorType().name());
		item.put("cursorValue", safeNullable(cursor.cursorValue()));
		item.put("enabled", cursor.enabled());
		item.put("lastSuccessAt", safeNullable(cursor.lastSuccessAt()));
		item.put("lastFailureAt", safeNullable(cursor.lastFailureAt()));
		item.put("failureCount", cursor.failureCount());
		item.put("updatedAt", cursor.updatedAt());
		return item;
	}

	private Map<String, Object> dataQualityDocument() {
		var dataQuality = contentSource.dataQuality();
		return Map.of("totalPlaces", dataQuality.totalPlaces(), "activePlaces", dataQuality.activePlaces(),
			"curationReadyPlaces", dataQuality.curationReadyPlaces());
	}

	private void considerSnapshot(GenerationState state, CallRow call) {
		if (!state.bundle.includeRawSnapshots() || call.snapshot() == null) {
			return;
		}
		if (call.provider() != ExternalApiProvider.KTO) {
			state.exclude(call.provider(), "PROVIDER_RETENTION_RESTRICTED");
			return;
		}
		SnapshotRow snapshot = call.snapshot();
		if (snapshot.retentionClass() != SnapshotRetentionClass.COMPETITION_EVIDENCE) {
			state.exclude(call.provider(), "PROVIDER_RETENTION_RESTRICTED");
			return;
		}
		if (snapshot.retentionUntil() != null && !snapshot.retentionUntil().isAfter(clock.instant())) {
			state.exclude(call.provider(), "EXPIRED");
			return;
		}
		String key = call.provider().name() + ":" + call.operation();
		if (state.snapshotCounts.getOrDefault(key, 0) >= state.bundle.rawSampleLimitPerOperation()) {
			state.exclude(call.provider(), "SAMPLE_LIMIT");
			return;
		}
		state.snapshotCounts.merge(key, 1, Integer::sum);
		state.snapshots.add(new SnapshotCandidate(call.provider(), call.operation(), snapshot));
	}

	private void writeRawSnapshots(ZipOutputStream zip, GenerationState state) throws IOException {
		for (SnapshotCandidate candidate : state.snapshots) {
			String extension = candidate.snapshot.storageFormat() == SnapshotStorageFormat.XML_GZIP ? "xml" : "json";
			String operation = candidate.operation.replaceAll("[^A-Za-z0-9._-]", "_");
			String path = "raw_snapshots/" + candidate.provider.name() + "/" + operation + "/"
				+ candidate.snapshot.snapshotId() + "." + extension + ".gz";
			ManifestFileRecord file = writeStreamingEntry(zip, path, output -> {
				try (InputStream input = snapshotReader.open(candidate.snapshot.storageKey())) {
					input.transferTo(output);
				}
			});
			state.files.add(file);
			state.rawSnapshots.add(new RawSnapshotManifest(candidate.provider, candidate.operation,
				candidate.snapshot.snapshotId(), candidate.snapshot.rawContentSha256(),
				candidate.snapshot.storedObjectSha256(), file.path(), file.sha256(), file.byteSize()));
			state.rawSnapshotCount++;
		}
	}

	private Map<String, Object> manifestDocument(GenerationState state) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("bundleId", state.bundle.bundleId());
		result.put("name", state.bundle.name());
		result.put("period", Map.of("from", state.bundle.from(), "to", state.bundle.to()));
		result.put("providers", state.bundle.providers().stream().map(Enum::name).toList());
		result.put("operations", state.bundle.operations());
		result.put("includeRawSnapshots", state.bundle.includeRawSnapshots());
		result.put("rawSampleLimitPerOperation", state.bundle.rawSampleLimitPerOperation());
		result.put("generatedAt", clock.instant());
		result.put("createdBySubject", state.bundle.createdBySubject());
		result.put("callCount", state.callCount);
		result.put("rawSnapshotCount", state.rawSnapshotCount);
		result.put("rawSnapshots", state.rawSnapshots.stream().map(item -> Map.of(
			"snapshotId", item.snapshotId(), "provider", item.provider().name(),
			"operation", item.operation(), "rawContentSha256", item.rawContentSha256(),
			"storedObjectSha256", item.storedObjectSha256(), "path", item.path(),
			"zipEntrySha256", item.zipEntrySha256(), "byteSize", item.byteSize())).toList());
		result.put("excluded", state.exclusions.values().stream().map(item -> Map.of(
			"provider", item.provider().name(), "reason", item.reason())).toList());
		result.put("files", state.files.stream().map(file -> Map.of(
			"path", file.path(), "sha256", file.sha256(), "byteSize", file.byteSize())).toList());
		return result;
	}

	private ManifestFileRecord writeJson(ZipOutputStream zip, String path, Object document) throws IOException {
		try {
			return writeBytes(zip, path, jsonMapper.writeValueAsBytes(document));
		} catch (JacksonException exception) {
			throw new IllegalStateException("Evidence JSON could not be generated", exception);
		}
	}

	private ManifestFileRecord writeShaSums(ZipOutputStream zip, List<ManifestFileRecord> files) throws IOException {
		StringBuilder sums = new StringBuilder();
		for (ManifestFileRecord file : files) {
			sums.append(file.sha256()).append("  ").append(file.path()).append('\n');
		}
		return writeBytes(zip, "SHA256SUMS", sums.toString().getBytes(StandardCharsets.UTF_8));
	}

	private ManifestFileRecord writeBytes(ZipOutputStream zip, String path, byte[] bytes) throws IOException {
		return writeStreamingEntry(zip, path, output -> output.write(bytes));
	}

	private ManifestFileRecord writeStreamingEntry(ZipOutputStream zip, String path, EntryWriter writer) throws IOException {
		zip.putNextEntry(new ZipEntry(path));
		MessageDigest digest = digest();
		CountingOutputStream counting = new CountingOutputStream(zip);
		DigestOutputStream output = new DigestOutputStream(counting, digest);
		try {
			writer.write(output);
			output.flush();
		} finally {
			zip.closeEntry();
		}
		return new ManifestFileRecord(path, HexFormat.of().formatHex(digest.digest()), counting.count());
	}

	private static final class CountingOutputStream extends FilterOutputStream {
		private long count;

		private CountingOutputStream(OutputStream output) {
			super(output);
		}

		@Override
		public void write(int value) throws IOException {
			out.write(value);
			count++;
		}

		@Override
		public void write(byte[] bytes, int offset, int length) throws IOException {
			out.write(bytes, offset, length);
			count += length;
		}

		@Override
		public void close() throws IOException {
			flush();
		}

		private long count() {
			return count;
		}
	}

	private BufferedWriter writer(OutputStream output) {
		return new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
			@Override
			public void close() throws IOException {
				flush();
			}
		};
	}

	private String sha256(Path path) throws IOException {
		try (InputStream input = Files.newInputStream(path)) {
			MessageDigest digest = digest();
			byte[] buffer = new byte[COPY_BUFFER_SIZE];
			for (int read; (read = input.read(buffer)) != -1;) {
				digest.update(buffer, 0, read);
			}
			return HexFormat.of().formatHex(digest.digest());
		}
	}

	private MessageDigest digest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private String csv(Object... values) {
		StringBuilder line = new StringBuilder();
		for (int index = 0; index < values.length; index++) {
			if (index > 0) {
				line.append(',');
			}
			String value = values[index] == null ? "" : values[index].toString();
			line.append('"').append(value.replace("\"", "\"\"")).append('"');
		}
		return line.toString();
	}

	private Object safeNullable(Object value) {
		return value == null ? "" : value;
	}

	@FunctionalInterface
	private interface EntryWriter {
		void write(OutputStream output) throws IOException;
	}

	private static final class GenerationState {
		private final BundleRecord bundle;
		private final Selection selection;
		private final List<ManifestFileRecord> files = new ArrayList<>();
		private final List<SnapshotCandidate> snapshots = new ArrayList<>();
		private final List<RawSnapshotManifest> rawSnapshots = new ArrayList<>();
		private final Map<String, Integer> snapshotCounts = new LinkedHashMap<>();
		private final Map<String, ExclusionRecord> exclusions = new LinkedHashMap<>();
		private long callCount;
		private long rawSnapshotCount;

		private GenerationState(BundleRecord bundle) {
			this.bundle = bundle;
			this.selection = new Selection(bundle.from(), bundle.to(), bundle.providers(), bundle.operations());
			if (bundle.includeRawSnapshots()) {
				for (ExternalApiProvider provider : bundle.providers()) {
					if (provider != ExternalApiProvider.KTO) {
						exclude(provider, "PROVIDER_RETENTION_RESTRICTED");
					}
				}
			}
		}

		private void exclude(ExternalApiProvider provider, String reason) {
			exclusions.putIfAbsent(provider.name() + ":" + reason, new ExclusionRecord(provider, reason));
		}
	}

	private record SnapshotCandidate(ExternalApiProvider provider, String operation, SnapshotRow snapshot) {
	}

	private record RawSnapshotManifest(
		ExternalApiProvider provider, String operation, long snapshotId, String rawContentSha256,
		String storedObjectSha256, String path, String zipEntrySha256, long byteSize
	) {
	}

	public record GeneratedArchive(
		Path path, String fileName, String sha256, long byteSize, long callCount,
		long rawSnapshotCount, List<ExclusionRecord> exclusions, List<ManifestFileRecord> manifestFiles
	) {
	}
}
