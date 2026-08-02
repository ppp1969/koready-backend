package koready_backend.terms.application.port;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import koready_backend.user.domain.SignupStatus;

public interface TermsRepository {

	Optional<UserState> findActiveUser(String publicId);

	Optional<UserState> findActiveUserForUpdate(String publicId);

	List<CurrentTerm> findCurrentTerms(long userId, Instant asOf);

	void saveAgreements(
		long userId,
		List<AgreementChange> agreements,
		Instant updatedAt);

	void updateSignupStatus(
		long userId,
		SignupStatus signupStatus,
		Instant updatedAt);

	record UserState(long userId, SignupStatus signupStatus) {
	}

	record CurrentTerm(
		long termId,
		long termVersionId,
		String code,
		String title,
		boolean required,
		String version,
		URI contentUrl,
		int displayOrder,
		boolean agreed,
		Instant agreedAt
	) {

		public CurrentTerm withAgreement(boolean nextAgreed, Instant nextAgreedAt) {
			return new CurrentTerm(
				termId,
				termVersionId,
				code,
				title,
				required,
				version,
				contentUrl,
				displayOrder,
				nextAgreed,
				nextAgreedAt);
		}
	}

	record AgreementChange(long termVersionId, boolean agreed) {
	}
}
