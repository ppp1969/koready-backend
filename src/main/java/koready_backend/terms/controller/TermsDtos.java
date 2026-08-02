package koready_backend.terms.controller;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import koready_backend.terms.application.TermsService;
import koready_backend.terms.application.TermsService.AgreementCommand;
import koready_backend.terms.application.port.TermsRepository.CurrentTerm;
import koready_backend.user.domain.NextStep;

final class TermsDtos {

	private TermsDtos() {
	}

	static RequiredTermsResponse from(TermsService.RequiredTermsResult result) {
		return new RequiredTermsResponse(
			result.terms().stream().map(TermsDtos::requiredTerm).toList(),
			result.allRequiredAgreed());
	}

	static TermAgreementResponse from(TermsService.AgreementResult result) {
		return new TermAgreementResponse(
			result.agreements().stream().map(TermsDtos::agreement).toList(),
			result.allRequiredAgreed(),
			result.nextStep());
	}

	private static RequiredTermItem requiredTerm(CurrentTerm term) {
		return new RequiredTermItem(
			term.termId(),
			term.termVersionId(),
			term.code(),
			term.title(),
			term.required(),
			term.version(),
			term.contentUrl().toString(),
			term.agreed(),
			!term.agreed(),
			term.displayOrder());
	}

	private static TermAgreementItem agreement(CurrentTerm term) {
		return new TermAgreementItem(
			term.termVersionId(),
			term.code(),
			term.required(),
			term.agreed(),
			term.agreedAt());
	}

	record AgreementRequest(
		@NotNull List<@Valid AgreementItem> agreements
	) {

		List<AgreementCommand> toCommands() {
			return agreements.stream()
				.map(item -> new AgreementCommand(
					item.termVersionId(), item.agreed()))
				.toList();
		}
	}

	record AgreementItem(
		@Positive long termVersionId,
		@NotNull Boolean agreed
	) {
	}

	record RequiredTermsResponse(
		List<RequiredTermItem> terms,
		boolean allRequiredAgreed
	) {
	}

	record RequiredTermItem(
		long termId,
		long termVersionId,
		String code,
		String title,
		boolean required,
		String version,
		String contentUrl,
		boolean agreed,
		boolean needsAgreement,
		int displayOrder
	) {
	}

	record TermAgreementResponse(
		List<TermAgreementItem> agreements,
		boolean allRequiredAgreed,
		NextStep nextStep
	) {
	}

	record TermAgreementItem(
		long termVersionId,
		String code,
		boolean required,
		boolean agreed,
		Instant agreedAt
	) {
	}
}
