package koready_backend.buddy.controller;

import java.util.Locale;

import koready_backend.place.domain.PlaceLanguage;

final class CountryDisplayName {

	private CountryDisplayName() {
	}

	static String of(String countryCode, PlaceLanguage language) {
		if (countryCode == null || countryCode.isBlank()) {
			return null;
		}
		Locale country = new Locale.Builder().setRegion(countryCode).build();
		return country.getDisplayCountry(
			language == PlaceLanguage.EN ? Locale.ENGLISH : Locale.KOREAN);
	}
}
