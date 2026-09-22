package koready_backend.kto.infrastructure.config;

import koready_backend.kto.application.KtoRequestQuotaService;
import koready_backend.kto.infrastructure.client.KtoQuotaInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class KtoPhotoAwardRestClientConfiguration {

	@Bean
	@Qualifier("ktoPhotoAwardRestClient")
	RestClient ktoPhotoAwardRestClient(KtoPhotoAwardApiProperties properties, KtoRequestQuotaService quota) {
		var requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.connectTimeout());
		requestFactory.setReadTimeout(properties.readTimeout());

		return RestClient.builder()
			.baseUrl(properties.baseUrl())
			.requestFactory(requestFactory)
			.requestInterceptor(new KtoQuotaInterceptor("photo-award", quota))
			.build();
	}
}
