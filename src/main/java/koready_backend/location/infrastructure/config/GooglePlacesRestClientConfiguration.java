package koready_backend.location.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
	name = "koready.location.search.provider",
	havingValue = "kakao")
class GooglePlacesRestClientConfiguration {

	@Bean("googlePlacesRestClient")
	RestClient googlePlacesRestClient(GooglePlacesProperties properties) {
		var requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.connectTimeout());
		requestFactory.setReadTimeout(properties.readTimeout());
		return RestClient.builder()
			.baseUrl(properties.baseUrl().toString())
			.requestFactory(requestFactory)
			.build();
	}
}
