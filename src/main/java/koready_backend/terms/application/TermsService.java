package koready_backend.terms.application;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.terms.application.exception.InvalidTermAgreementException;
import koready_backend.terms.application.exception.RequiredTermsNotAgreedException;
import koready_backend.terms.application.exception.TermsUserUnavailableException;
import koready_backend.terms.application.port.TermsRepository;
import koready_backend.terms.application.port.TermsRepository.AgreementChange;
import koready_backend.terms.application.port.TermsRepository.CurrentTerm;
import koready_backend.terms.application.port.TermsRepository.UserState;
import koready_backend.user.domain.NextStep;
import koready_backend.user.domain.SignupStatus;

@Service
public class TermsService {

	private final TermsRepository repository;
	private final Clock clock;

	@Autowired
	public TermsService(TermsRepository repository) {
		this(repository, Clock.systemUTC());
	}

	TermsService(TermsRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public RequiredTermsResult getRequiredTerms(String userPublicId) {
		Instant now = clock.instant();
		UserState user = repository.findActiveUser(userPublicId)
			.orElseThrow(TermsUserUnavailableException::new);
		List<CurrentTerm> terms =
			repository.findCurrentTerms(user.userId(), now);
		return new RequiredTermsResult(terms, allRequiredAgreed(terms));
	}

	@Transactional
	public AgreementResult updateAgreements(
		String userPublicId,
		List<AgreementCommand> commands
	) {
		Instant now = clock.instant();
		UserState user = repository.findActiveUserForUpdate(userPublicId)
			.orElseThrow(TermsUserUnavailableException::new);
		List<CurrentTerm> currentTerms =
			repository.findCurrentTerms(user.userId(), now);
		Map<Long, AgreementCommand> submitted = validate(commands, currentTerms);

		boolean requiredAgreed = currentTerms.stream()
			.filter(CurrentTerm::required)
			.allMatch(term -> submitted.containsKey(term.termVersionId())
				? submitted.get(term.termVersionId()).agreed()
				: term.agreed());
		if (!requiredAgreed) {
			throw new RequiredTermsNotAgreedException();
		}

		List<AgreementChange> changes = commands.stream()
			.map(command -> new AgreementChange(
				command.termVersionId(), command.agreed()))
			.toList();
		if (!changes.isEmpty()) {
			repository.saveAgreements(user.userId(), changes, now);
		}

		SignupStatus nextStatus = user.signupStatus().afterTermsAgreement();
		if (nextStatus != user.signupStatus()) {
			repository.updateSignupStatus(user.userId(), nextStatus, now);
		}

		List<CurrentTerm> updatedTerms =
			repository.findCurrentTerms(user.userId(), now);
		return new AgreementResult(
			updatedTerms,
			allRequiredAgreed(updatedTerms),
			nextStatus.nextStep());
	}

	private static Map<Long, AgreementCommand> validate(
		List<AgreementCommand> commands,
		List<CurrentTerm> currentTerms
	) {
		Set<Long> currentVersionIds = currentTerms.stream()
			.map(CurrentTerm::termVersionId)
			.collect(Collectors.toSet());
		Map<Long, AgreementCommand> submitted = new HashMap<>();
		for (AgreementCommand command : commands) {
			if (command == null
				|| command.termVersionId() <= 0
				|| !currentVersionIds.contains(command.termVersionId())
				|| submitted.putIfAbsent(command.termVersionId(), command) != null) {
				throw new InvalidTermAgreementException();
			}
		}
		return submitted;
	}

	private static boolean allRequiredAgreed(List<CurrentTerm> terms) {
		return terms.stream()
			.filter(CurrentTerm::required)
			.allMatch(CurrentTerm::agreed);
	}

	public record AgreementCommand(long termVersionId, boolean agreed) {
	}

	public record RequiredTermsResult(
		List<CurrentTerm> terms,
		boolean allRequiredAgreed
	) {
	}

	public record AgreementResult(
		List<CurrentTerm> agreements,
		boolean allRequiredAgreed,
		NextStep nextStep
	) {
	}
}
