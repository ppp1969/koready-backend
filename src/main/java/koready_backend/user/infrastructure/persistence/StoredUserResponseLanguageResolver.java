package koready_backend.user.infrastructure.persistence;

import org.springframework.stereotype.Component;

import koready_backend.place.application.port.ResponseLanguageResolver;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.user.application.port.UserLanguageRepository;

@Component
public class StoredUserResponseLanguageResolver implements ResponseLanguageResolver {

	private final UserLanguageRepository repository;

	public StoredUserResponseLanguageResolver(UserLanguageRepository repository) {
		this.repository = repository;
	}

	@Override
	public PlaceLanguage resolve(String userPublicId, String acceptLanguage) {
		PlaceLanguage fallback = PlaceLanguage.fromAcceptLanguage(acceptLanguage);
		if (userPublicId == null || userPublicId.isBlank()) {
			return fallback;
		}
		return repository.findByPublicId(userPublicId)
			.map(UserLanguageRepository.UserLanguageState::language)
			.orElse(fallback);
	}
}
