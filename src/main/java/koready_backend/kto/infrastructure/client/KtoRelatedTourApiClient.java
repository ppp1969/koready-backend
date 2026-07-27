package koready_backend.kto.infrastructure.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import koready_backend.kto.application.exception.KtoClientConfigurationException;
import koready_backend.kto.application.exception.KtoProviderException;
import koready_backend.kto.application.exception.KtoResponseParseException;
import koready_backend.kto.application.exception.KtoResponseTooLargeException;
import koready_backend.kto.application.exception.KtoTransportException;
import koready_backend.kto.application.model.KtoFetchedRelatedTourPage;
import koready_backend.kto.application.model.KtoSuccessfulCallMetadata;
import koready_backend.kto.application.port.KtoRelatedTourPageClient;
import koready_backend.kto.domain.KtoRelatedTourPage;
import koready_backend.kto.infrastructure.config.KtoBatchProperties;
import koready_backend.kto.infrastructure.config.KtoRelatedTourApiProperties;

@Component
public final class KtoRelatedTourApiClient
	implements KtoRelatedTourPageClient {

	private static final String OPERATION_PATH = "/areaBasedList1";
	private static final int READ_BUFFER_BYTES = 8 * 1024;
	private static final int MAX_TRANSIENT_ATTEMPTS = 4;
	private static final long INITIAL_RETRY_DELAY_MILLIS = 1_000;

	private final RestClient restClient;
	private final KtoRelatedTourApiProperties apiProperties;
	private final KtoBatchProperties batchProperties;
	private final KtoRelatedTourResponseParser parser;
	private final Clock clock;
	private final RetrySleeper retrySleeper;

	@Autowired
	public KtoRelatedTourApiClient(
		@Qualifier("ktoRelatedTourRestClient") RestClient restClient,
		KtoRelatedTourApiProperties apiProperties,
		KtoBatchProperties batchProperties,
		KtoRelatedTourResponseParser parser
	) {
		this(
			restClient,
			apiProperties,
			batchProperties,
			parser,
			Clock.systemUTC(),
			Thread::sleep);
	}

	KtoRelatedTourApiClient(
		RestClient restClient,
		KtoRelatedTourApiProperties apiProperties,
		KtoBatchProperties batchProperties,
		KtoRelatedTourResponseParser parser,
		Clock clock,
		RetrySleeper retrySleeper
	) {
		this.restClient = restClient;
		this.apiProperties = apiProperties;
		this.batchProperties = batchProperties;
		this.parser = parser;
		this.clock = clock;
		this.retrySleeper = retrySleeper;
	}

	@Override
	public KtoFetchedRelatedTourPage fetchPage(
		String baseYearMonth,
		String areaCode,
		String signguCode,
		int pageNumber
	) {
		validateRequest(
			baseYearMonth, areaCode, signguCode, pageNumber);
		if (apiProperties.serviceKey() == null
			|| apiProperties.serviceKey().isBlank()) {
			throw new KtoClientConfigurationException(
				"KTO related tour service key is not configured");
		}
		for (int attempt = 1;
			attempt <= MAX_TRANSIENT_ATTEMPTS;
			attempt++) {
			try {
				return fetchPageOnce(
					baseYearMonth,
					areaCode,
					signguCode,
					pageNumber);
			} catch (KtoTransportException
				| KtoProviderException exception) {
				if (!retryable(exception)
					|| attempt == MAX_TRANSIENT_ATTEMPTS) {
					throw exception;
				}
				pause(attempt);
			}
		}
		throw new IllegalStateException(
			"KTO related tour retry attempts were exhausted");
	}

	private KtoFetchedRelatedTourPage fetchPageOnce(
		String baseYearMonth,
		String areaCode,
		String signguCode,
		int pageNumber
	) {
		Instant requestedAt = clock.instant();
		long startedNanos = System.nanoTime();
		try {
			return restClient.get()
				.uri(uriBuilder -> uriBuilder
					.path(OPERATION_PATH)
					.queryParam(
						"numOfRows", batchProperties.pageSize())
					.queryParam("pageNo", pageNumber)
					.queryParam("MobileOS", apiProperties.mobileOs())
					.queryParam(
						"MobileApp", apiProperties.mobileApp())
					.queryParam("_type", "json")
					.queryParam("baseYm", baseYearMonth)
					.queryParam("areaCd", areaCode)
					.queryParam("signguCd", signguCode)
					.queryParam(
						"serviceKey", apiProperties.serviceKey())
					.build())
				.accept(MediaType.APPLICATION_JSON)
				.exchange((request, response) -> {
					if (!response.getStatusCode().is2xxSuccessful()) {
						throw KtoProviderException.forHttpStatus(
							response.getStatusCode().value());
					}
					long contentLength =
						response.getHeaders().getContentLength();
					if (contentLength
						> apiProperties.maxResponseBytes()) {
						throw new KtoResponseTooLargeException(
							apiProperties.maxResponseBytes());
					}
					byte[] payload = readBounded(
						response.getBody(),
						apiProperties.maxResponseBytes());
					KtoRelatedTourPage page = normalizePage(
						parser.parse(payload), pageNumber);
					Instant receivedAt = clock.instant();
					long durationMs =
						TimeUnit.NANOSECONDS.toMillis(
							System.nanoTime() - startedNanos);
					return new KtoFetchedRelatedTourPage(
						page,
						new KtoSuccessfulCallMetadata(
							requestedAt,
							receivedAt,
							Math.max(0, durationMs),
							response.getStatusCode().value()),
						payload);
				});
		} catch (KtoProviderException
			| KtoResponseTooLargeException exception) {
			throw exception;
		} catch (RestClientException exception) {
			throw new KtoTransportException();
		}
	}

	private boolean retryable(RuntimeException exception) {
		if (exception instanceof KtoTransportException) {
			return true;
		}
		if (exception instanceof KtoProviderException providerException) {
			String code = providerException.providerCode();
			return "22".equals(code)
				|| "HTTP_429".equals(code)
				|| code.matches("HTTP_5\\d\\d");
		}
		return false;
	}

	private void pause(int completedAttempt) {
		try {
			retrySleeper.sleep(
				INITIAL_RETRY_DELAY_MILLIS
					* (1L << (completedAttempt - 1)));
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new KtoTransportException();
		}
	}

	private static void validateRequest(
		String baseYearMonth,
		String areaCode,
		String signguCode,
		int pageNumber
	) {
		if (baseYearMonth == null
			|| !baseYearMonth.matches("\\d{6}")
			|| areaCode == null
			|| !areaCode.matches("\\d{2,10}")
			|| signguCode == null
			|| !signguCode.matches("\\d{2,10}")
			|| pageNumber < 1) {
			throw new IllegalArgumentException(
				"KTO related tour request is invalid");
		}
	}

	private KtoRelatedTourPage normalizePage(
		KtoRelatedTourPage parsed,
		int requestedPageNumber
	) {
		if (parsed.pageNumber() != requestedPageNumber
			|| parsed.items().size() > batchProperties.pageSize()) {
			throw new KtoResponseParseException(
				"KTO related tour pagination metadata is invalid");
		}
		if (parsed.pageSize() == batchProperties.pageSize()) {
			return parsed;
		}
		return new KtoRelatedTourPage(
			parsed.pageNumber(),
			batchProperties.pageSize(),
			parsed.totalCount(),
			parsed.items(),
			parsed.responseBytes(),
			parsed.responseSha256());
	}

	private byte[] readBounded(
		InputStream input,
		int maxResponseBytes
	) throws IOException {
		var output = new ByteArrayOutputStream(
			Math.min(64 * 1024, maxResponseBytes));
		byte[] buffer = new byte[READ_BUFFER_BYTES];
		int totalBytes = 0;
		while (true) {
			int bytesToRead = Math.min(
				buffer.length, maxResponseBytes - totalBytes + 1);
			int bytesRead = input.read(buffer, 0, bytesToRead);
			if (bytesRead == -1) {
				return output.toByteArray();
			}
			if (bytesRead > maxResponseBytes - totalBytes) {
				throw new KtoResponseTooLargeException(maxResponseBytes);
			}
			output.write(buffer, 0, bytesRead);
			totalBytes += bytesRead;
		}
	}

	@FunctionalInterface
	interface RetrySleeper {
		void sleep(long delayMillis) throws InterruptedException;
	}
}
