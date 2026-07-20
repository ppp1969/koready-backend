package koready_backend.batch.infrastructure.kto;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.stereotype.Component;

import koready_backend.batch.application.port.BatchJobExecutionRepository.ClaimedJob;
import koready_backend.batch.application.port.KtoBatchJobRunner;
import koready_backend.batch.domain.BatchJobType;
import koready_backend.kto.application.KtoDailySyncImportService;
import koready_backend.kto.application.KtoFestivalImportService;
import koready_backend.kto.application.model.KtoDailySyncRequest;
import koready_backend.kto.application.model.KtoFestivalImportRequest;

@Component
public class KtoBatchJobRunnerAdapter implements KtoBatchJobRunner {

	private final KtoDailySyncImportService dailySyncService;
	private final KtoFestivalImportService festivalImportService;

	public KtoBatchJobRunnerAdapter(
		KtoDailySyncImportService dailySyncService,
		KtoFestivalImportService festivalImportService
	) {
		this.dailySyncService = dailySyncService;
		this.festivalImportService = festivalImportService;
	}

	@Override
	public RunResult run(ClaimedJob job) {
		int startPage = integer(job.parameters(), "startPage");
		int maxPages = integer(job.parameters(), "maxPages");
		if (job.jobType() == BatchJobType.KTO_DAILY_SYNC) {
			var result = dailySyncService.sync(new KtoDailySyncRequest(startPage, maxPages));
			return new RunResult(result.processedItems(), result.processedItems(), 0);
		}
		if (job.jobType() == BatchJobType.KTO_FESTIVAL_SYNC) {
			var result = festivalImportService.importFestivals(new KtoFestivalImportRequest(
				LocalDate.parse(string(job.parameters(), "eventStartDate")), startPage, maxPages));
			return new RunResult(result.processedItems(), result.processedItems(), 0);
		}
		throw new IllegalArgumentException("Unsupported manual batch job type");
	}

	private static int integer(Map<String, Object> parameters, String name) {
		Object value = parameters.get(name);
		if (!(value instanceof Number number)) {
			throw new IllegalArgumentException("Batch job parameter is invalid");
		}
		return number.intValue();
	}

	private static String string(Map<String, Object> parameters, String name) {
		Object value = parameters.get(name);
		if (!(value instanceof String text) || text.isBlank()) {
			throw new IllegalArgumentException("Batch job parameter is invalid");
		}
		return text;
	}
}
