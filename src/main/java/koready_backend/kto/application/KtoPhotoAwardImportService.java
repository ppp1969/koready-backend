package koready_backend.kto.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import koready_backend.kto.application.exception.KtoResponseParseException;
import koready_backend.kto.application.model.KtoBatchExecutionReference;
import koready_backend.kto.application.model.KtoFetchedPhotoAwardPage;
import koready_backend.kto.application.model.KtoPhotoAwardImportRequest;
import koready_backend.kto.application.model.KtoPhotoAwardImportResult;
import koready_backend.kto.application.model.KtoPhotoAwardStorePageCommand;
import koready_backend.kto.application.model.KtoPhotoAwardStorePageResult;
import koready_backend.kto.application.model.KtoRawSnapshot;
import koready_backend.kto.application.model.KtoStoredSnapshotMetadata;
import koready_backend.kto.application.port.KtoPhotoAwardPageClient;
import koready_backend.kto.application.port.KtoPhotoAwardStore;
import koready_backend.kto.application.port.KtoRawSnapshotStore;
import koready_backend.kto.domain.KtoPhotoAwardPage;

@Service
public class KtoPhotoAwardImportService {

	private static final String SNAPSHOT_SERVICE = "photo-award";
	private static final String OPERATION = "phokoAwrdSyncList";

	private final KtoPhotoAwardPageClient client;
	private final KtoRawSnapshotStore snapshotStore;
	private final KtoPhotoAwardStore store;
	private final Clock clock;

	@Autowired
	public KtoPhotoAwardImportService(
		KtoPhotoAwardPageClient client,
		KtoRawSnapshotStore snapshotStore,
		KtoPhotoAwardStore store
	) {
		this(client, snapshotStore, store, Clock.systemUTC());
	}

	KtoPhotoAwardImportService(
		KtoPhotoAwardPageClient client,
		KtoRawSnapshotStore snapshotStore,
		KtoPhotoAwardStore store,
		Clock clock
	) {
		this.client = client;
		this.snapshotStore = snapshotStore;
		this.store = store;
		this.clock = clock;
	}

	public KtoPhotoAwardImportResult importAwards(
		KtoPhotoAwardImportRequest request
	) {
		return importAwards(request, null);
	}

	public KtoPhotoAwardImportResult importAwards(
		KtoPhotoAwardImportRequest request,
		KtoBatchExecutionReference batchExecution
	) {
		int processedPages = 0;
		int processedItems = 0;
		int replayedPages = 0;
		int reportedTotalCount = 0;
		int lastProcessedPage = request.startPage();
		int reportedLastPage = Integer.MAX_VALUE;

		for (int pageNumber = request.startPage();
			processedPages < request.maxPages()
				&& pageNumber <= reportedLastPage;
			pageNumber++) {
			KtoFetchedPhotoAwardPage fetched = client.fetchPage(pageNumber);
			KtoPhotoAwardPage page = fetched.page();
			if (page.pageNumber() != pageNumber) {
				throw new KtoResponseParseException(
					"KTO photo award page number did not match the request");
			}
			reportedTotalCount = page.totalCount();
			reportedLastPage = page.totalCount() == 0
				? pageNumber
				: (page.totalCount() + page.pageSize() - 1) / page.pageSize();
			Instant capturedAt = clock.instant();
			LocalDate snapshotDate = LocalDate.now(clock);
			KtoStoredSnapshotMetadata snapshot = snapshotStore.store(
				new KtoRawSnapshot(
					SNAPSHOT_SERVICE,
					OPERATION,
					snapshotDate,
					pageNumber,
					page.responseSha256(),
					fetched.rawPayload(),
					capturedAt));
			KtoPhotoAwardStorePageResult stored = store.store(
				new KtoPhotoAwardStorePageCommand(
					page,
					fetched.call(),
					snapshot,
					batchExecution));

			processedPages++;
			processedItems += stored.processedCount();
			replayedPages += stored.replayed() ? 1 : 0;
			lastProcessedPage = pageNumber;
		}

		return new KtoPhotoAwardImportResult(
			request.startPage(),
			processedPages,
			processedItems,
			replayedPages,
			reportedTotalCount,
			lastProcessedPage,
			lastProcessedPage < reportedLastPage);
	}
}
