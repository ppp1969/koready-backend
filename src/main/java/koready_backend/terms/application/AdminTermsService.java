package koready_backend.terms.application;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.terms.application.port.AdminTermsRepository;
import koready_backend.terms.application.port.AdminTermsRepository.TermDefinition;
import koready_backend.terms.application.port.AdminTermsRepository.TermVersion;

@Service
public class AdminTermsService {
	private final AdminTermsRepository repository;
	private final Clock clock;

	@Autowired
	public AdminTermsService(AdminTermsRepository repository) { this(repository, Clock.systemUTC()); }
	AdminTermsService(AdminTermsRepository repository, Clock clock) { this.repository = repository; this.clock = clock; }

	@Transactional(readOnly = true)
	public List<TermDefinition> list() { return repository.findAll(); }

	@Transactional
	public TermDefinition createDefinition(String code, int displayOrder, boolean enabled) {
		String normalized = code.trim().toUpperCase(Locale.ROOT);
		if (!normalized.matches("[A-Z][A-Z0-9_]{1,49}")) throw new IllegalArgumentException("Invalid term code.");
		return repository.createDefinition(normalized, displayOrder, enabled, clock.instant());
	}

	@Transactional
	public TermDefinition updateDefinition(long id, int displayOrder, boolean enabled) {
		return repository.updateDefinition(id, displayOrder, enabled, clock.instant())
			.orElseThrow(AdminTermNotFoundException::new);
	}

	@Transactional
	public TermVersion createVersion(long termId, VersionCommand command) {
		return repository.createVersion(termId, command.version(), command.title(), command.contentUrl(),
			command.required(), command.effectiveAt(), clock.instant()).orElseThrow(AdminTermNotFoundException::new);
	}

	@Transactional
	public TermVersion updateDraft(long termId, long versionId, VersionCommand command) {
		return repository.updateDraft(termId, versionId, command.version(), command.title(), command.contentUrl(),
			command.required(), command.effectiveAt(), clock.instant())
			.orElseThrow(() -> new AdminTermConflictException("Only an existing draft can be edited."));
	}

	@Transactional
	public TermVersion publish(long termId, long versionId) {
		TermVersion current = repository.findVersion(termId, versionId).orElseThrow(AdminTermNotFoundException::new);
		if (current.publishedAt() != null || current.contentUrl() == null)
			throw new AdminTermConflictException("Only a draft with a content URL can be published.");
		return repository.publish(termId, versionId, clock.instant())
			.orElseThrow(() -> new AdminTermConflictException("The version is no longer publishable."));
	}

	@Transactional
	public TermVersion withdraw(long termId, long versionId) {
		return repository.withdraw(termId, versionId, clock.instant())
			.orElseThrow(() -> new AdminTermConflictException("Only a published version can be withdrawn."));
	}

	public record VersionCommand(String version, String title, URI contentUrl, boolean required, Instant effectiveAt) {}
}
