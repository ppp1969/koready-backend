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
import koready_backend.terms.application.port.AdminTermsRepository.TermTranslation;
import koready_backend.terms.application.port.AdminTermsRepository.TermVersion;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.terms.domain.TermContentFormat;

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
		VersionCommand normalized = normalize(command);
		TermVersion created = repository.createVersion(termId, normalized.version(), normalized.title(), normalized.contentUrl(),
			normalized.content(), normalized.contentFormat(), normalized.required(), normalized.effectiveAt(),
			clock.instant()).orElseThrow(AdminTermNotFoundException::new);
		repository.replaceTranslations(created.id(), normalized.translations(), clock.instant());
		return repository.findVersion(termId, created.id()).orElseThrow(AdminTermNotFoundException::new);
	}

	@Transactional
	public TermVersion updateDraft(long termId, long versionId, VersionCommand command) {
		VersionCommand normalized = normalize(command);
		TermVersion updated = repository.updateDraft(termId, versionId, normalized.version(), normalized.title(),
			normalized.contentUrl(), normalized.content(), normalized.contentFormat(), normalized.required(),
			normalized.effectiveAt(), clock.instant())
			.orElseThrow(() -> new AdminTermConflictException("Only an existing draft can be edited."));
		if (!normalized.translations().isEmpty()) {
			repository.replaceTranslations(updated.id(), normalized.translations(), clock.instant());
		}
		return repository.findVersion(termId, updated.id()).orElseThrow(AdminTermNotFoundException::new);
	}

	@Transactional
	public TermVersion publish(long termId, long versionId) {
		TermVersion current = repository.findVersion(termId, versionId).orElseThrow(AdminTermNotFoundException::new);
		if (current.publishedAt() != null || !hasCompleteContent(current))
			throw new AdminTermConflictException("Only a draft with one complete content source can be published.");
		return repository.publish(termId, versionId, clock.instant())
			.orElseThrow(() -> new AdminTermConflictException("The version is no longer publishable."));
	}

	@Transactional
	public TermVersion withdraw(long termId, long versionId) {
		return repository.withdraw(termId, versionId, clock.instant())
			.orElseThrow(() -> new AdminTermConflictException("Only a published version can be withdrawn."));
	}

	private static VersionCommand normalize(VersionCommand command) {
		List<TermTranslation> translations = normalizeTranslations(command.translations());
		if (!translations.isEmpty()) {
			TermTranslation korean = translations.stream().filter(value -> value.language() == PlaceLanguage.KO)
				.findFirst().orElseThrow(() -> new IllegalArgumentException("KO translation is required."));
			if (translations.stream().noneMatch(value -> value.language() == PlaceLanguage.EN))
				throw new IllegalArgumentException("EN translation is required.");
			command = new VersionCommand(command.version(), korean.title(), korean.contentUrl(), korean.content(),
				korean.contentFormat(), command.required(), command.effectiveAt(), translations);
		}
		if (command.title() == null || command.title().isBlank())
			throw new IllegalArgumentException("title is required.");
		boolean hasUrl = command.contentUrl() != null;
		boolean hasContent = command.content() != null && !command.content().isBlank();
		if (hasUrl && (hasContent || command.contentFormat() != null))
			throw new IllegalArgumentException("Choose either content or contentUrl, not both.");
		if (!hasContent && command.contentFormat() != null)
			throw new IllegalArgumentException("contentFormat requires content.");
		if (command.content() != null && !hasContent)
			throw new IllegalArgumentException("content must not be blank.");
		if (hasUrl) validateUrl(command.contentUrl());
		if (hasContent && command.content().length() > 100_000)
			throw new IllegalArgumentException("content must be at most 100000 characters.");
		TermContentFormat format = hasContent
			? (command.contentFormat() == null ? TermContentFormat.MARKDOWN : command.contentFormat()) : null;
		return new VersionCommand(command.version().trim(), command.title().trim(), command.contentUrl(),
			command.content(), format, command.required(), command.effectiveAt(), translations);
	}

	private static List<TermTranslation> normalizeTranslations(List<TermTranslation> values) {
		if (values == null || values.isEmpty()) return List.of();
		if (values.stream().map(TermTranslation::language).distinct().count() != values.size())
			throw new IllegalArgumentException("Each language can appear only once.");
		return values.stream().map(value -> {
			if (value.language() == null || value.title() == null || value.title().isBlank())
				throw new IllegalArgumentException("Each translation requires language and title.");
			boolean hasUrl = value.contentUrl() != null;
			boolean hasContent = value.content() != null && !value.content().isBlank();
			if (hasUrl == hasContent) throw new IllegalArgumentException("Each translation requires exactly one content source.");
			if (hasUrl) validateUrl(value.contentUrl());
			TermContentFormat format = hasContent
				? (value.contentFormat() == null ? TermContentFormat.MARKDOWN : value.contentFormat()) : null;
			if (hasUrl && value.contentFormat() != null)
				throw new IllegalArgumentException("contentFormat requires inline content.");
			return new TermTranslation(value.language(), value.title().trim(), value.contentUrl(), value.content(), format);
		}).toList();
	}

	private static void validateUrl(URI url) {
		if (!url.isAbsolute() || !"https".equalsIgnoreCase(url.getScheme())
			|| url.getHost() == null || url.getHost().isBlank() || url.getUserInfo() != null)
			throw new IllegalArgumentException("contentUrl must be an absolute HTTPS URL without credentials.");
	}

	private static boolean hasCompleteContent(TermVersion version) {
		boolean baseComplete = version.contentUrl() != null
			|| (version.content() != null && !version.content().isBlank() && version.contentFormat() != null);
		if (version.translations().isEmpty()) return baseComplete;
		return version.translations().stream().map(TermTranslation::language).distinct().count() == 2
			&& version.translations().stream().allMatch(value -> value.contentUrl() != null
				|| (value.content() != null && !value.content().isBlank() && value.contentFormat() != null));
	}

	public record VersionCommand(String version, String title, URI contentUrl, String content,
		TermContentFormat contentFormat, boolean required, Instant effectiveAt,
		List<TermTranslation> translations) {
		public VersionCommand(String version, String title, URI contentUrl, String content,
			TermContentFormat contentFormat, boolean required, Instant effectiveAt) {
			this(version, title, contentUrl, content, contentFormat, required, effectiveAt, List.of());
		}
	}
}
