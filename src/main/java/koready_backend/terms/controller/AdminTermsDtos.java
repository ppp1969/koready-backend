package koready_backend.terms.controller;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import koready_backend.terms.application.AdminTermsService.VersionCommand;
import koready_backend.terms.application.port.AdminTermsRepository.TermDefinition;
import koready_backend.terms.application.port.AdminTermsRepository.TermVersion;

final class AdminTermsDtos {
	private AdminTermsDtos() {}
	static List<DefinitionResponse> from(List<TermDefinition> terms) { return terms.stream().map(AdminTermsDtos::from).toList(); }
	static DefinitionResponse from(TermDefinition term) { return new DefinitionResponse(term.id(), term.code(), term.displayOrder(), term.enabled(), term.versions().stream().map(AdminTermsDtos::from).toList()); }
	static VersionResponse from(TermVersion version) {
		String state = version.withdrawnAt() != null ? "WITHDRAWN" : version.publishedAt() == null ? "DRAFT" : "PUBLISHED";
		return new VersionResponse(version.id(), version.termId(), version.version(), version.title(),
			version.contentUrl() == null ? null : version.contentUrl().toString(), version.required(), version.effectiveAt(), version.publishedAt(), version.withdrawnAt(), state);
	}
	record CreateDefinitionRequest(@NotBlank @Pattern(regexp="[A-Za-z][A-Za-z0-9_]{1,49}") String code,
		@Min(1) @Max(1000) int displayOrder, boolean enabled) {}
	record UpdateDefinitionRequest(@Min(1) @Max(1000) int displayOrder, boolean enabled) {}
	record VersionRequest(@NotBlank @Size(max=40) String version, @NotBlank @Size(max=200) String title,
		@Size(max=2048) String contentUrl, boolean required, @NotNull Instant effectiveAt) {
		VersionCommand command() { return new VersionCommand(version.trim(), title.trim(),
			contentUrl == null || contentUrl.isBlank() ? null : URI.create(contentUrl), required, effectiveAt); }
	}
	record DefinitionResponse(long id, String code, int displayOrder, boolean enabled, List<VersionResponse> versions) {}
	record VersionResponse(long id, long termId, String version, String title, String contentUrl, boolean required,
		Instant effectiveAt, Instant publishedAt, Instant withdrawnAt, String state) {}
}
