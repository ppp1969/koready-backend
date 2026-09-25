package koready_backend.kto.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import koready_backend.kto.application.port.KtoRequestQuotaRepository;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JdbcKtoRequestQuotaRepositoryIntegrationTest {
	@Container @ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");
	@Autowired KtoRequestQuotaRepository repository;
	@Autowired JdbcTemplate jdbc;

	@Test
	void atomicReservationsDoNotExceedTheLimitAcrossCompetingWorkers() throws Exception {
		LocalDate day = LocalDate.of(2026, 1, 1);
		try (var pool = Executors.newFixedThreadPool(4)) {
			var tasks = new ArrayList<Callable<Boolean>>();
			for (int i = 0; i < 40; i++) { tasks.add(() -> repository.reserve(day, "kor-detailcommon2", 17)); }
			int allowed = 0;
			for (var result : pool.invokeAll(tasks)) { if (result.get()) { allowed++; } }
			assertEquals(17, allowed);
		}
		assertFalse(repository.reserve(day, "kor-detailcommon2", 17));
		assertTrue(repository.reserve(day, "kor-detailcommon2", 18));
		assertTrue(repository.reserve(day, "eng-detailcommon2", 1));
		assertTrue(repository.reserve(day.plusDays(1), "kor-detailcommon2", 1));
		assertFalse(repository.reserve(day, "kor-disabled", 0));
		assertEquals(18, jdbc.queryForObject("SELECT reserved_requests FROM kto_daily_request_usage WHERE usage_date = ? AND operation_key = ?",
			Integer.class, day, "kor-detailcommon2"));
	}

	@Test
	void migrationPausesKnownOperationsForItsFirstDayWithoutPretendingTheyWereCalled() {
		assertEquals(11, jdbc.queryForObject("SELECT COUNT(*) FROM kto_daily_request_usage WHERE blocked = TRUE AND reserved_requests = 0", Integer.class));
		LocalDate day = jdbc.queryForObject("SELECT usage_date FROM kto_daily_request_usage WHERE blocked = TRUE LIMIT 1", LocalDate.class);
		assertFalse(repository.reserve(day, "kor-detailcommon2", 100000));
	}
}
