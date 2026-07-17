package koready_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KoreadyBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(KoreadyBackendApplication.class, args);
	}

}
