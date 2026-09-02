package koready_backend.terms.application.port;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AdminTermsRepository {
	List<TermDefinition> findAll();
	TermDefinition createDefinition(String code, int displayOrder, boolean enabled, Instant now);
	Optional<TermDefinition> updateDefinition(long id, int displayOrder, boolean enabled, Instant now);
	Optional<TermVersion> findVersion(long termId, long versionId);
	Optional<TermVersion> createVersion(long termId, String version, String title, URI contentUrl,
		boolean required, Instant effectiveAt, Instant now);
	Optional<TermVersion> updateDraft(long termId, long versionId, String version, String title, URI contentUrl,
		boolean required, Instant effectiveAt, Instant now);
	Optional<TermVersion> publish(long termId, long versionId, Instant now);
	Optional<TermVersion> withdraw(long termId, long versionId, Instant now);

	record TermDefinition(long id, String code, int displayOrder, boolean enabled, List<TermVersion> versions) {}
	record TermVersion(long id, long termId, String version, String title, URI contentUrl, boolean required,
		Instant effectiveAt, Instant publishedAt, Instant withdrawnAt) {}
}
