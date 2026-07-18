package koready_backend.kto.application.port;

import java.time.LocalDate;

import koready_backend.kto.application.model.KtoFetchedFestivalPage;

public interface KtoFestivalPageClient {

	KtoFetchedFestivalPage fetchPage(LocalDate eventStartDate, int pageNumber);
}
