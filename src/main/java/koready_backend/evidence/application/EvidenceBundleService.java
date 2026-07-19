package koready_backend.evidence.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.evidence.application.exception.EvidenceBundleDownloadUnavailableException;
import koready_backend.evidence.application.exception.EvidenceBundleNotCompletedException;
import koready_backend.evidence.application.exception.EvidenceBundleNotFoundException;
import koready_backend.evidence.application.exception.InvalidEvidenceBundlePeriodException;
import koready_backend.evidence.application.port.EvidenceBundleArtifactStore;
import koready_backend.evidence.application.port.EvidenceBundleRepository;
import koready_backend.evidence.application.port.EvidenceBundleRepository.AuditRecord;
import koready_backend.evidence.application.port.EvidenceBundleRepository.BundleRecord;
import koready_backend.evidence.domain.EvidenceBundleStatus;
import koready_backend.externalapi.domain.ExternalApiProvider;

@Service
public class EvidenceBundleService {

	private static final int MAX_NAME_LENGTH = 200;
	private static final int MAX_OPERATION_LENGTH = 100;
	private static final int MAX_OPERATIONS = 100;
	private static final int MAX_PAGE_SIZE = 100;
	private static final int MAX_ACTOR_LENGTH = 191;

	private final EvidenceBundleRepository repository;
	private final EvidenceBundleArtifactStore artifactStore;
	private final Clock clock;

	@Autowired
	public EvidenceBundleService(
		EvidenceBundleRepository repository,
		EvidenceBundleArtifactStore artifactStore
	) {
		this(repository, artifactStore, Clock.systemUTC());
	}

	EvidenceBundleService(
		EvidenceBundleRepository repository,
		EvidenceBundleArtifactStore artifactStore,
		Clock clock
	) {
		this.repository = repository;
		this.artifactStore = artifactStore;
		this.clock = clock;
	}

	@Transactional
	public BundleView create(CreateCommand command, String actorSubject) {
		CreateCommand normalized = normalize(command);
		String actor = requireActor(actorSubject);
		BundleRecord created = repository.create(new EvidenceBundleRepository.CreateRecord(
			"evidence_" + UUID.randomUUID().toString().replace("-", ""),
			normalized.name(), normalized.from(), normalized.to(), normalized.providers(),
			normalized.operations(), normalized.includeRawSnapshots(),
			normalized.rawSampleLimitPerOperation(), actor, clock.instant()));
		repository.recordAudit(new AuditRecord(
			actor, "EVIDENCE_BUNDLE_QUEUED", created.bundleId(),
			Map.of("providers", created.providers().stream().map(Enum::name).toList(),
				"includeRawSnapshots", created.includeRawSnapshots()), clock.instant()));
		return view(created);
	}

	@Transactional(readOnly = true)
	public BundlePage list(String cursor, int size) {
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("Evidence bundle page size is invalid");
		}
		Long beforeId = decodeCursor(cursor);
		List<BundleRecord> records = repository.findPage(beforeId, size + 1);
		boolean hasMore = records.size() > size;
		List<BundleRecord> page = hasMore ? records.subList(0, size) : records;
		String nextCursor = hasMore ? encodeCursor(page.getLast().id()) : null;
		return new BundlePage(page.stream().map(this::view).toList(), nextCursor, hasMore);
	}

	@Transactional(readOnly = true)
	public BundleView get(String bundleId) {
		return view(find(bundleId));
	}

	@Transactional
	public DownloadView createDownloadUrl(String bundleId, String actorSubject) {
		BundleRecord bundle = find(bundleId);
		if (bundle.status() != EvidenceBundleStatus.COMPLETED
			|| bundle.storageKey() == null || bundle.fileName() == null || bundle.sha256() == null) {
			throw new EvidenceBundleNotCompletedException();
		}
		EvidenceBundleArtifactStore.DownloadLink link;
		try {
			link = artifactStore.createDownloadUrl(bundle.storageKey(), bundle.fileName());
		} catch (RuntimeException exception) {
			throw new EvidenceBundleDownloadUnavailableException();
		}
		repository.recordAudit(new AuditRecord(
			requireActor(actorSubject), "EVIDENCE_BUNDLE_DOWNLOAD_URL_ISSUED", bundle.bundleId(),
			Map.of("sha256", bundle.sha256(), "expiresAt", link.expiresAt().toString()), clock.instant()));
		return new DownloadView(link.url(), link.expiresAt(), bundle.fileName(), bundle.sha256());
	}

	private BundleRecord find(String bundleId) {
		if (bundleId == null || !bundleId.matches("evidence_[a-zA-Z0-9]{8,64}")) {
			throw new EvidenceBundleNotFoundException();
		}
		return repository.findByBundleId(bundleId).orElseThrow(EvidenceBundleNotFoundException::new);
	}

	private CreateCommand normalize(CreateCommand command) {
		if (command == null) {
			throw new IllegalArgumentException("Evidence bundle request is required");
		}
		String name = requireText(command.name(), MAX_NAME_LENGTH, "Evidence bundle name is invalid");
		if (command.from() == null || command.to() == null || !command.from().isBefore(command.to())) {
			throw new InvalidEvidenceBundlePeriodException();
		}
		if (command.providers() == null || command.providers().isEmpty()) {
			throw new IllegalArgumentException("Evidence bundle providers are required");
		}
		List<ExternalApiProvider> providers = List.copyOf(new LinkedHashSet<>(command.providers()));
		if (providers.size() != command.providers().size()
			|| command.providers().stream().anyMatch(provider -> provider == null)) {
			throw new IllegalArgumentException("Evidence bundle providers are invalid");
		}
		if (command.rawSampleLimitPerOperation() < 0 || command.rawSampleLimitPerOperation() > 100) {
			throw new IllegalArgumentException("Evidence raw snapshot sample limit is invalid");
		}
		List<String> operations = new ArrayList<>();
		if (command.operations() != null) {
			if (command.operations().size() > MAX_OPERATIONS) {
				throw new IllegalArgumentException("Too many evidence operations");
			}
			for (String operation : command.operations()) {
				operations.add(requireText(operation, MAX_OPERATION_LENGTH, "Evidence operation is invalid"));
			}
		}
		operations = List.copyOf(new LinkedHashSet<>(operations));
		return new CreateCommand(name, command.from(), command.to(), providers, operations,
			command.includeRawSnapshots(), command.rawSampleLimitPerOperation());
	}

	private String requireActor(String actor) {
		return requireText(actor, MAX_ACTOR_LENGTH, "Evidence bundle actor is invalid");
	}

	private String requireText(String value, int maxLength, String message) {
		if (value == null) {
			throw new IllegalArgumentException(message);
		}
		String normalized = value.trim();
		if (normalized.isEmpty() || normalized.length() > maxLength) {
			throw new IllegalArgumentException(message);
		}
		return normalized;
	}

	private Long decodeCursor(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return null;
		}
		try {
			String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			long value = Long.parseLong(decoded);
			if (value <= 0) {
				throw new IllegalArgumentException("Evidence bundle cursor is invalid");
			}
			return value;
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Evidence bundle cursor is invalid");
		}
	}

	private String encodeCursor(long id) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(
			Long.toString(id).getBytes(StandardCharsets.UTF_8));
	}

	private BundleView view(BundleRecord record) {
		return new BundleView(record.bundleId(), record.name(), record.status(),
			new Period(record.from(), record.to()), record.providers(), record.operations(),
			record.includeRawSnapshots(), record.fileName(), record.sha256(), record.byteSize(),
			record.callCount(), record.rawSnapshotCount(), record.createdAt(), record.startedAt(),
			record.finishedAt(), record.failureReason(),
			record.exclusions().stream().map(item -> new ExclusionView(item.provider(), item.reason())).toList(),
			record.manifestFiles().stream().map(item -> new ManifestFileView(item.path(), item.sha256(), item.byteSize())).toList());
	}

	public record CreateCommand(
		String name, Instant from, Instant to, List<ExternalApiProvider> providers,
		List<String> operations, boolean includeRawSnapshots, int rawSampleLimitPerOperation
	) {
	}

	public record Period(Instant from, Instant to) {
	}

	public record BundleView(
		String bundleId, String name, EvidenceBundleStatus status, Period period,
		List<ExternalApiProvider> providers, List<String> operations, boolean includeRawSnapshots,
		String fileName, String sha256, Long byteSize, Long callCount, Long rawSnapshotCount,
		Instant createdAt, Instant startedAt, Instant finishedAt, String failureReason,
		List<ExclusionView> excluded, List<ManifestFileView> manifestFiles
	) {
	}

	public record BundlePage(List<BundleView> items, String nextCursor, boolean hasMore) {
	}

	public record ExclusionView(ExternalApiProvider provider, String reason) {
	}

	public record ManifestFileView(String path, String sha256, long byteSize) {
	}

	public record DownloadView(String downloadUrl, Instant expiresAt, String fileName, String sha256) {
	}
}
