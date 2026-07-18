package koready_backend.kto.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import koready_backend.kto.application.exception.KtoResponseParseException;
import koready_backend.kto.application.model.KtoFetchedFestivalPage;
import koready_backend.kto.application.model.KtoFestivalImportRequest;
import koready_backend.kto.application.model.KtoFestivalImportResult;
import koready_backend.kto.application.model.KtoFestivalStorePageResult;
import koready_backend.kto.application.model.KtoRawSnapshot;
import koready_backend.kto.application.model.KtoStoreFestivalPageCommand;
import koready_backend.kto.application.model.KtoStoredSnapshotMetadata;
import koready_backend.kto.application.port.KtoFestivalPageClient;
import koready_backend.kto.application.port.KtoFestivalPageStore;
import koready_backend.kto.application.port.KtoRawSnapshotStore;
import koready_backend.kto.domain.KtoFestivalPage;

@Service
public class KtoFestivalImportService {

	private static final String OPERATION = "searchFestival2";

	private final KtoFestivalPageClient client;
	private final KtoRawSnapshotStore snapshotStore;
	private final KtoFestivalPageStore pageStore;
	private final Clock clock;

	@Autowired
	public KtoFestivalImportService(
		KtoFestivalPageClient client,
		KtoRawSnapshotStore snapshotStore,
		KtoFestivalPageStore pageStore
	) {
		this(client, snapshotStore, pageStore, Clock.systemUTC());
	}

	KtoFestivalImportService(
		KtoFestivalPageClient client,
		KtoRawSnapshotStore snapshotStore,
		KtoFestivalPageStore pageStore,
		Clock clock
	) {
		this.client = client;
		this.snapshotStore = snapshotStore;
		this.pageStore = pageStore;
		this.clock = clock;
	}

	public KtoFestivalImportResult importFestivals(KtoFestivalImportRequest request) {
		int processedPages = 0;
		int processedItems = 0;
		int replayedPages = 0;
		int reportedTotalCount = 0;
		int lastProcessedPage = request.startPage();
		int reportedLastPage = Integer.MAX_VALUE;

		for (int pageNumber = request.startPage();
			processedPages < request.maxPages() && pageNumber <= reportedLastPage;
			pageNumber++) {
			KtoFetchedFestivalPage fetched = client.fetchPage(request.eventStartDate(), pageNumber);
			KtoFestivalPage page = fetched.page();
			if (page.pageNumber() != pageNumber) {
				throw new KtoResponseParseException("KTO festival page number did not match the request");
			}

			reportedTotalCount = page.totalCount();
			reportedLastPage = page.totalCount() == 0
				? pageNumber
				: (page.totalCount() + page.pageSize() - 1) / page.pageSize();
			Instant capturedAt = Instant.now(clock);
			KtoStoredSnapshotMetadata snapshot = snapshotStore.store(new KtoRawSnapshot(
				OPERATION,
				request.eventStartDate(),
				pageNumber,
				page.responseSha256(),
				fetched.rawPayload(),
				capturedAt));
			KtoFestivalStorePageResult stored = pageStore.store(new KtoStoreFestivalPageCommand(
				request.eventStartDate(),
				page,
				fetched.call(),
				snapshot));

			processedPages++;
			processedItems += stored.processedCount();
			replayedPages += stored.replayed() ? 1 : 0;
			lastProcessedPage = pageNumber;
		}

		boolean truncated = lastProcessedPage < reportedLastPage;
		return new KtoFestivalImportResult(
			request.eventStartDate(),
			request.startPage(),
			processedPages,
			processedItems,
			replayedPages,
			reportedTotalCount,
			lastProcessedPage,
			truncated);
	}
}
