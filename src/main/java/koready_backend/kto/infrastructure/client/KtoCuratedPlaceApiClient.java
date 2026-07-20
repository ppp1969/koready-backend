package koready_backend.kto.infrastructure.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import koready_backend.kto.application.exception.KtoClientConfigurationException;
import koready_backend.kto.application.exception.KtoProviderException;
import koready_backend.kto.application.exception.KtoResponseTooLargeException;
import koready_backend.kto.application.exception.KtoTransportException;
import koready_backend.kto.application.port.KtoCuratedPlaceClient;
import koready_backend.kto.domain.KtoPlaceDetail;
import koready_backend.kto.domain.KtoPlaceImage;
import koready_backend.kto.domain.KtoPlaceItem;
import koready_backend.kto.infrastructure.config.KtoApiProperties;

@Component
public final class KtoCuratedPlaceApiClient implements KtoCuratedPlaceClient {

	private static final String SEARCH_PATH = "/searchKeyword2";
	private static final String DETAIL_PATH = "/detailCommon2";
	private static final String DETAIL_IMAGE_PATH = "/detailImage2";
	private static final int SEARCH_RESULT_LIMIT = 10;
	private static final int READ_BUFFER_BYTES = 8 * 1024;

	private final RestClient restClient;
	private final KtoApiProperties apiProperties;
	private final KtoCuratedPlaceResponseParser parser;

	public KtoCuratedPlaceApiClient(
		@Qualifier("ktoRestClient") RestClient restClient,
		KtoApiProperties apiProperties,
		KtoCuratedPlaceResponseParser parser
	) {
		this.restClient = restClient;
		this.apiProperties = apiProperties;
		this.parser = parser;
	}

	@Override
	public List<KtoPlaceItem> search(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			throw new IllegalArgumentException("KTO curated search keyword is required");
		}
		requireServiceKey();
		byte[] payload = request(uriBuilder -> uriBuilder
			.path(SEARCH_PATH)
			.queryParam("numOfRows", SEARCH_RESULT_LIMIT)
			.queryParam("pageNo", 1)
			.queryParam("MobileOS", apiProperties.mobileOs())
			.queryParam("MobileApp", apiProperties.mobileApp())
			.queryParam("_type", "json")
			.queryParam("keyword", keyword.strip())
			.queryParam("serviceKey", apiProperties.serviceKey())
			.build());
		return parser.parseSearch(payload);
	}

	@Override
	public KtoPlaceDetail fetchDetail(String contentId) {
		if (contentId == null || contentId.isBlank()) {
			throw new IllegalArgumentException("KTO curated content ID is required");
		}
		requireServiceKey();
		byte[] payload = request(uriBuilder -> uriBuilder
			.path(DETAIL_PATH)
			.queryParam("MobileOS", apiProperties.mobileOs())
			.queryParam("MobileApp", apiProperties.mobileApp())
			.queryParam("_type", "json")
			.queryParam("contentId", contentId.strip())
			.queryParam("serviceKey", apiProperties.serviceKey())
			.build());
		return parser.parseDetail(payload);
	}

	@Override
	public List<KtoPlaceImage> fetchImages(String contentId) {
		if (contentId == null || contentId.isBlank()) {
			throw new IllegalArgumentException("KTO curated content ID is required");
		}
		requireServiceKey();
		byte[] payload = request(uriBuilder -> uriBuilder
			.path(DETAIL_IMAGE_PATH)
			.queryParam("MobileOS", apiProperties.mobileOs())
			.queryParam("MobileApp", apiProperties.mobileApp())
			.queryParam("_type", "json")
			.queryParam("contentId", contentId.strip())
			.queryParam("imageYN", "Y")
			.queryParam("serviceKey", apiProperties.serviceKey())
			.build());
		return parser.parseImages(payload);
	}

	private byte[] request(java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI> uri) {
		try {
			return restClient.get()
				.uri(uri)
				.accept(MediaType.APPLICATION_JSON)
				.exchange((request, response) -> {
					if (!response.getStatusCode().is2xxSuccessful()) {
						throw KtoProviderException.forHttpStatus(response.getStatusCode().value());
					}
					long contentLength = response.getHeaders().getContentLength();
					if (contentLength > apiProperties.maxResponseBytes()) {
						throw new KtoResponseTooLargeException(apiProperties.maxResponseBytes());
					}
					return readBounded(response.getBody(), apiProperties.maxResponseBytes());
				});
		} catch (KtoProviderException | KtoResponseTooLargeException exception) {
			throw exception;
		} catch (RestClientException exception) {
			throw new KtoTransportException();
		}
	}

	private void requireServiceKey() {
		if (apiProperties.serviceKey() == null || apiProperties.serviceKey().isBlank()) {
			throw new KtoClientConfigurationException("KTO service key is not configured");
		}
	}

	private byte[] readBounded(InputStream input, int maxResponseBytes) throws IOException {
		int initialCapacity = Math.min(64 * 1024, maxResponseBytes);
		var output = new ByteArrayOutputStream(initialCapacity);
		byte[] buffer = new byte[READ_BUFFER_BYTES];
		int totalBytes = 0;

		while (true) {
			int bytesToRead = Math.min(buffer.length, maxResponseBytes - totalBytes + 1);
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
