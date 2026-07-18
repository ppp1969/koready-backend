package koready_backend.user.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.place.domain.PlaceLanguage;
import koready_backend.user.application.exception.UserUnavailableException;
import koready_backend.user.application.port.UserLanguageRepository;
import koready_backend.user.application.port.UserLanguageRepository.UserLanguageState;
import koready_backend.user.domain.NextStep;
import koready_backend.user.domain.SignupStatus;

@Service
public class UserLanguageService {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	private final UserLanguageRepository repository;
	private final Clock clock;

	@Autowired
	public UserLanguageService(UserLanguageRepository repository) {
		this(repository, Clock.system(SEOUL_ZONE));
	}

	UserLanguageService(UserLanguageRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional
	public LanguageResult update(String userPublicId, PlaceLanguage language) {
		UserLanguageState current = repository.findByPublicIdForUpdate(userPublicId)
			.orElseThrow(UserUnavailableException::new);
		SignupStatus nextStatus = current.signupStatus().afterLanguageSelection();
		UserLanguageState result = current;
		if (current.language() != language || current.signupStatus() != nextStatus) {
			result = repository.update(
				current.userId(), language, nextStatus, clock.instant());
		}
		return new LanguageResult(
			result.language(), result.signupStatus().nextStep(), result.updatedAt());
	}

	public record LanguageResult(
		PlaceLanguage language,
		NextStep nextStep,
		Instant updatedAt
	) {
	}
}
