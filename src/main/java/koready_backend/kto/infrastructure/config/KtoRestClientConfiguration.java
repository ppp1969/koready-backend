package koready_backend.kto.infrastructure.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class KtoRestClientConfiguration {

	@Bean
	@Qualifier("ktoRestClient")
	RestClient ktoRestClient(KtoApiProperties properties) {
		var requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.connectTimeout());
		requestFactory.setReadTimeout(properties.readTimeout());

		return RestClient.builder()
			.baseUrl(properties.baseUrl())
			.requestFactory(requestFactory)
			.build();
	}
}
