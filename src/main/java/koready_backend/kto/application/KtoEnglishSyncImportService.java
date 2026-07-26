package koready_backend.kto.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import koready_backend.kto.application.exception.KtoResponseParseException;
import koready_backend.kto.application.model.KtoBatchExecutionReference;
import koready_backend.kto.application.model.KtoEnglishStorePageCommand;
import koready_backend.kto.application.model.KtoEnglishSyncRequest;
import koready_backend.kto.application.model.KtoEnglishSyncResult;
import koready_backend.kto.application.model.KtoRawSnapshot;
import koready_backend.kto.application.model.KtoStoredSnapshotMetadata;
import koready_backend.kto.application.port.KtoEnglishPageStore;
import koready_backend.kto.application.port.KtoEnglishSyncPageClient;
import koready_backend.kto.application.port.KtoRawSnapshotStore;

@Service
public class KtoEnglishSyncImportService {

	private static final String OPERATION = "areaBasedSyncList2";
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final KtoEnglishSyncPageClient client;
	private final KtoEnglishMatchService matchService;
	private final KtoRawSnapshotStore snapshotStore;
	private final KtoEnglishPageStore pageStore;
	private final Clock clock;

	@Autowired
	public KtoEnglishSyncImportService(
		KtoEnglishSyncPageClient client,
		KtoEnglishMatchService matchService,
		KtoRawSnapshotStore snapshotStore,
		KtoEnglishPageStore pageStore
	) {
		this(client, matchService, snapshotStore, pageStore, Clock.systemUTC());
	}

	KtoEnglishSyncImportService(
		KtoEnglishSyncPageClient client,
		KtoEnglishMatchService matchService,
		KtoRawSnapshotStore snapshotStore,
		KtoEnglishPageStore pageStore,
		Clock clock
	) {
		this.client = client;
		this.matchService = matchService;
		this.snapshotStore = snapshotStore;
		this.pageStore = pageStore;
		this.clock = clock;
	}

	public KtoEnglishSyncResult sync(
		KtoEnglishSyncRequest request,
		KtoBatchExecutionReference batchExecution
	) {
		int processedPages = 0;
		int processedItems = 0;
		int autoMatchedItems = 0;
		int reviewRequiredItems = 0;
		int unmatchedItems = 0;
		int localizedItems = 0;
		int replayedPages = 0;
		int reportedTotalCount = 0;
		int lastProcessedPage = request.startPage();
		int reportedLastPage = Integer.MAX_VALUE;
		boolean refreshMatches = request.startPage() == 1;

		for (int pageNumber = request.startPage();
			processedPages < request.maxPages() && pageNumber <= reportedLastPage;
			pageNumber++) {
			var fetched = client.fetchFetchedPage(pageNumber);
			var page = fetched.page();
			if (page.pageNumber() != pageNumber) {
				throw new KtoResponseParseException("KTO English page number did not match the request");
			}
			reportedTotalCount = page.totalCount();
			reportedLastPage = page.totalCount() == 0
				? pageNumber
				: (page.totalCount() + page.pageSize() - 1) / page.pageSize();
			Instant capturedAt = Instant.now(clock);
			KtoStoredSnapshotMetadata snapshot = snapshotStore.store(new KtoRawSnapshot(
				"eng",
				OPERATION,
				LocalDate.ofInstant(capturedAt, SEOUL),
				pageNumber,
				page.responseSha256(),
				fetched.rawPayload(),
				capturedAt));
			var matches = matchService.match(page.items(), refreshMatches);
			refreshMatches = false;
			var stored = pageStore.store(new KtoEnglishStorePageCommand(
				page, matches, fetched.call(), snapshot, batchExecution));
			processedPages++;
			processedItems += stored.processedCount();
			autoMatchedItems += stored.autoMatchedCount();
			reviewRequiredItems += stored.reviewRequiredCount();
			unmatchedItems += stored.unmatchedCount();
			localizedItems += stored.localizedCount();
			replayedPages += stored.replayed() ? 1 : 0;
			lastProcessedPage = pageNumber;
		}

		return new KtoEnglishSyncResult(
			processedPages,
			processedItems,
			autoMatchedItems,
			reviewRequiredItems,
			unmatchedItems,
			localizedItems,
			replayedPages,
			reportedTotalCount,
			lastProcessedPage,
			lastProcessedPage < reportedLastPage);
	}
}
