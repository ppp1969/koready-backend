package koready_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
class MySqlApplicationTests {

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Test
	void contextLoadsWithMySql() {
	}
}
