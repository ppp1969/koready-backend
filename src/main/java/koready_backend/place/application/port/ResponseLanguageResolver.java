package koready_backend.place.application.port;

import koready_backend.place.domain.PlaceLanguage;

public interface ResponseLanguageResolver {

	PlaceLanguage resolve(String userPublicId, String acceptLanguage);
}
