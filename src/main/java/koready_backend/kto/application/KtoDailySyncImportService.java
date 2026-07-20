package koready_backend.kto.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import koready_backend.kto.application.exception.KtoResponseParseException;
import koready_backend.kto.application.model.KtoBatchExecutionReference;
import koready_backend.kto.application.model.KtoDailySyncRequest;
import koready_backend.kto.application.model.KtoDailySyncResult;
import koready_backend.kto.application.model.KtoFetchedSyncPage;
import koready_backend.kto.application.model.KtoRawSnapshot;
import koready_backend.kto.application.model.KtoStorePageCommand;
import koready_backend.kto.application.model.KtoStorePageResult;
import koready_backend.kto.application.model.KtoStoredSnapshotMetadata;
import koready_backend.kto.application.port.KtoDailySyncPageClient;
import koready_backend.kto.application.port.KtoPageStore;
import koready_backend.kto.application.port.KtoRawSnapshotStore;
import koready_backend.kto.domain.KtoSyncPage;

@Service
public class KtoDailySyncImportService {

	private static final String OPERATION = "areaBasedSyncList2";
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final KtoDailySyncPageClient client;
	private final KtoRawSnapshotStore snapshotStore;
	private final KtoPageStore pageStore;
	private final Clock clock;

	@Autowired
	public KtoDailySyncImportService(
		KtoDailySyncPageClient client,
		KtoRawSnapshotStore snapshotStore,
		KtoPageStore pageStore
	) {
		this(client, snapshotStore, pageStore, Clock.systemUTC());
	}

	KtoDailySyncImportService(
		KtoDailySyncPageClient client,
		KtoRawSnapshotStore snapshotStore,
		KtoPageStore pageStore,
		Clock clock
	) {
		this.client = client;
		this.snapshotStore = snapshotStore;
		this.pageStore = pageStore;
		this.clock = clock;
	}

	public KtoDailySyncResult sync(KtoDailySyncRequest request) {
		return sync(request, null);
	}

	public KtoDailySyncResult sync(
		KtoDailySyncRequest request,
		KtoBatchExecutionReference batchExecution
	) {
		int processedPages = 0;
		int processedItems = 0;
		int replayedPages = 0;
		int reportedTotalCount = 0;
		int lastProcessedPage = request.startPage();
		int reportedLastPage = Integer.MAX_VALUE;

		for (int pageNumber = request.startPage();
			processedPages < request.maxPages() && pageNumber <= reportedLastPage;
			pageNumber++) {
			KtoFetchedSyncPage fetched = client.fetchFetchedPage(pageNumber);
			KtoSyncPage page = fetched.page();
			if (page.pageNumber() != pageNumber) {
				throw new KtoResponseParseException("KTO sync page number did not match the request");
			}
			reportedTotalCount = page.totalCount();
			reportedLastPage = page.totalCount() == 0
				? pageNumber
				: (page.totalCount() + page.pageSize() - 1) / page.pageSize();
			Instant capturedAt = Instant.now(clock);
			KtoStoredSnapshotMetadata snapshot = snapshotStore.store(new KtoRawSnapshot(
				OPERATION,
				LocalDate.ofInstant(capturedAt, SEOUL),
				pageNumber,
				page.responseSha256(),
				fetched.rawPayload(),
				capturedAt));
			KtoStorePageResult stored = pageStore.store(new KtoStorePageCommand(
				page,
				fetched.call(),
				snapshot,
				batchExecution));
			processedPages++;
			processedItems += stored.processedCount();
			replayedPages += stored.replayed() ? 1 : 0;
			lastProcessedPage = pageNumber;
		}

		return new KtoDailySyncResult(
			processedPages,
			processedItems,
			replayedPages,
			reportedTotalCount,
			lastProcessedPage,
			lastProcessedPage < reportedLastPage);
	}
}
