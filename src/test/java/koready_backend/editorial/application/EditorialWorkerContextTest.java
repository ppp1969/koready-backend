package koready_backend.editorial.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import koready_backend.editorial.application.port.EditorialGenerator;
import koready_backend.editorial.application.port.EditorialWorkerRepository;

class EditorialWorkerContextTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues(
			"koready.editorial.worker.enabled=true",
			"koready.editorial.worker.runtime-enabled=true",
			"koready.editorial.worker.max-attempts=2",
			"koready.editorial.worker.daily-limit=100"
		)
		.withUserConfiguration(EditorialWorker.class, WorkerDependencies.class);

	@Test
	void createsWorkerWhenRuntimeIsEnabled() {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(EditorialWorker.class);
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class WorkerDependencies {

		@Bean
		EditorialWorkerRepository repository() {
			return mock(EditorialWorkerRepository.class);
		}

		@Bean
		EditorialGenerator generator() {
			return mock(EditorialGenerator.class);
		}

		@Bean
		EditorialOutputValidator validator() {
			return new EditorialOutputValidator();
		}

	}
}
