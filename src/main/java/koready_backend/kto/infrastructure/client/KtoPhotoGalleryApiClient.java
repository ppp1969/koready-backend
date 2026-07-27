package koready_backend.kto.infrastructure.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

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
import koready_backend.kto.application.model.KtoFetchedPhotoGalleryPage;
import koready_backend.kto.application.model.KtoSuccessfulCallMetadata;
import koready_backend.kto.application.port.KtoPhotoGalleryPageClient;
import koready_backend.kto.domain.KtoPhotoGalleryPage;
import koready_backend.kto.infrastructure.config.KtoBatchProperties;
import koready_backend.kto.infrastructure.config.KtoPhotoGalleryApiProperties;

@Component
public final class KtoPhotoGalleryApiClient
	implements KtoPhotoGalleryPageClient {

	private static final String OPERATION_PATH = "/galleryList1";
	private static final int READ_BUFFER_BYTES = 8 * 1024;

	private final RestClient restClient;
	private final KtoPhotoGalleryApiProperties apiProperties;
	private final KtoBatchProperties batchProperties;
	private final KtoPhotoGalleryResponseParser parser;

	public KtoPhotoGalleryApiClient(
		@Qualifier("ktoPhotoGalleryRestClient") RestClient restClient,
		KtoPhotoGalleryApiProperties apiProperties,
		KtoBatchProperties batchProperties,
		KtoPhotoGalleryResponseParser parser
	) {
		this.restClient = restClient;
		this.apiProperties = apiProperties;
		this.batchProperties = batchProperties;
		this.parser = parser;
	}

	@Override
	public KtoFetchedPhotoGalleryPage fetchPage(int pageNumber) {
		if (pageNumber < 1) {
			throw new IllegalArgumentException(
				"KTO photo gallery page number must be at least 1");
		}
		if (apiProperties.serviceKey() == null
			|| apiProperties.serviceKey().isBlank()) {
			throw new KtoClientConfigurationException(
				"KTO photo gallery service key is not configured");
		}
		Instant requestedAt = Instant.now();
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
					KtoPhotoGalleryPage page = normalizePage(
						parser.parse(payload), pageNumber);
					Instant receivedAt = Instant.now();
					long durationMs =
						TimeUnit.NANOSECONDS.toMillis(
							System.nanoTime() - startedNanos);
					return new KtoFetchedPhotoGalleryPage(
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

	private KtoPhotoGalleryPage normalizePage(
		KtoPhotoGalleryPage parsed,
		int requestedPageNumber
	) {
		if (parsed.pageNumber() != requestedPageNumber
			|| parsed.items().size() > batchProperties.pageSize()) {
			throw new KtoResponseParseException(
				"KTO photo gallery pagination metadata is invalid");
		}
		if (parsed.pageSize() == batchProperties.pageSize()) {
			return parsed;
		}
		return new KtoPhotoGalleryPage(
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
}
