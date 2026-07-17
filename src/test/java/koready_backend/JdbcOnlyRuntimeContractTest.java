package koready_backend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class JdbcOnlyRuntimeContractTest {

	private static final List<String> DATABASE_CONFIGS = List.of(
		"application.yml",
		"application-local.yml",
		"application-test.yml",
		"application-staging.yml",
		"application-prod.yml");

	@Test
	void excludesJpaAndHibernateFromTheRuntimeClasspath() {
		assertThrows(ClassNotFoundException.class, () -> Class.forName("org.hibernate.Session"));
		assertThrows(
			ClassNotFoundException.class,
			() -> Class.forName("org.springframework.data.jpa.repository.JpaRepository"));
		assertThrows(ClassNotFoundException.class, () -> Class.forName("jakarta.persistence.EntityManager"));
	}

	@Test
	void leavesSchemaOwnershipToFlywayInEveryDatabaseProfile() throws IOException {
		for (String profile : DATABASE_CONFIGS) {
			try (InputStream input = getClass().getClassLoader().getResourceAsStream(profile)) {
				assertNotNull(input, () -> "Missing profile: " + profile);
				Map<String, Object> yaml = new Yaml().load(input);
				Map<?, ?> spring = (Map<?, ?>) yaml.get("spring");
				assertFalse(spring.containsKey("jpa"), () -> "JPA settings remain in " + profile);
			}
		}
	}
}
