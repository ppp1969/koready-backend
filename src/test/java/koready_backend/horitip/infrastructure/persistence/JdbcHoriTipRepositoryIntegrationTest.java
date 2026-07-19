package koready_backend.horitip.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import koready_backend.horitip.application.port.HoriTipRepository;
import koready_backend.horitip.application.port.HoriTipRepository.AuditRecord;
import koready_backend.horitip.application.port.HoriTipRepository.HoriTipRecord;
import koready_backend.horitip.application.port.HoriTipRepository.ListCriteria;
import koready_backend.horitip.application.port.HoriTipRepository.NewHoriTip;
import koready_backend.horitip.domain.HoriTipDraft;
import koready_backend.horitip.domain.HoriTipPlacement;
import koready_backend.horitip.domain.HoriTipRouteMode;
import koready_backend.horitip.domain.HoriTipScope;
import koready_backend.horitip.domain.HoriTipScopeType;
import koready_backend.horitip.domain.HoriTipStatus;
import koready_backend.horitip.domain.HoriTipTranslation;
import koready_backend.horitip.domain.HoriTipTrigger;
import koready_backend.place.domain.PlaceLanguage;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class JdbcHoriTipRepositoryIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-07-19T08:00:00.123456Z");

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	HoriTipRepository repository;

	private long firstPlace;
	private long secondPlace;

	@BeforeEach
	void setUp() {
		Integer migration = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '10'",
			Integer.class);
		assertEquals(1, migration);
		firstPlace = place("hori-tip-first", true);
		secondPlace = place("hori-tip-second", true);
	}

	@Test
	void insertsLoadsAndProtectsThePermanentCode() {
		HoriTipDraft draft = draft(List.of(firstPlace), false);
		HoriTipRecord created = repository.insertDraft(new NewHoriTip(
			"TIP_INTEGRATION",
			draft,
			"admin",
			NOW)).orElseThrow();

		HoriTipRecord loaded = repository.findById(created.id()).orElseThrow();
		assertEquals("TIP_INTEGRATION", loaded.code());
		assertEquals(HoriTipStatus.DRAFT, loaded.status());
		assertEquals(List.of(firstPlace), loaded.draft().scope().destinationPlaceIds());
		assertEquals(List.of(HoriTipRouteMode.TRAIN), loaded.draft().trigger().segmentModes());
		assertEquals(PlaceLanguage.KO, loaded.draft().translations().getFirst().language());
		assertEquals(NOW, loaded.createdAt());
		assertTrue(repository.insertDraft(new NewHoriTip(
			"TIP_INTEGRATION",
			draft,
			"admin",
			NOW)).isEmpty());
	}

	@Test
	void atomicallyReplacesEditableChildrenAndRecordsAuditSnapshots() {
		HoriTipRecord before = repository.insertDraft(new NewHoriTip(
			"TIP_REPLACE",
			draft(List.of(firstPlace), false),
			"admin",
			NOW)).orElseThrow();
		HoriTipDraft replacement = draft(List.of(secondPlace), true);

		HoriTipRecord after = repository.updateDraft(
			before.id(), replacement, "operator", NOW.plusSeconds(1));
		repository.recordAudit(new AuditRecord(
			"HORI_TIP_UPDATED",
			"operator",
			null,
			before,
			after,
			NOW.plusSeconds(1)));

		assertEquals(2, after.version());
		assertEquals(List.of(secondPlace), after.draft().scope().destinationPlaceIds());
		assertEquals(2, after.draft().translations().size());
		assertEquals("operator", after.updatedBySubject());
		assertEquals(1, count("hori_tip_audit_logs"));
		assertEquals(1, jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*) FROM hori_tip_audit_logs
			WHERE before_snapshot IS NOT NULL AND after_snapshot IS NOT NULL
			""",
			Integer.class));
	}

	@Test
	void filtersByStatusCodeDestinationAndStableIdBoundary() {
		HoriTipRecord first = repository.insertDraft(new NewHoriTip(
			"TIP_ALPHA",
			draft(List.of(firstPlace), false),
			"admin",
			NOW)).orElseThrow();
		HoriTipRecord second = repository.insertDraft(new NewHoriTip(
			"TIP_BETA",
			draft(List.of(secondPlace), false),
			"admin",
			NOW)).orElseThrow();

		List<HoriTipRecord> filtered = repository.findPage(new ListCriteria(
			HoriTipStatus.DRAFT,
			"ALPHA",
			firstPlace,
			null,
			10));
		List<HoriTipRecord> afterBoundary = repository.findPage(new ListCriteria(
			null,
			null,
			null,
			second.id(),
			10));

		assertEquals(List.of(first.id()), filtered.stream().map(HoriTipRecord::id).toList());
		assertTrue(afterBoundary.stream().allMatch(item -> item.id() < second.id()));
		assertEquals(Set.of(firstPlace, secondPlace),
			repository.findVisiblePlaceIds(List.of(firstPlace, secondPlace)));
		jdbcTemplate.update("UPDATE places SET show_flag = FALSE WHERE id = ?", secondPlace);
		assertEquals(Set.of(firstPlace),
			repository.findVisiblePlaceIds(List.of(firstPlace, secondPlace)));
	}

	@Test
	void incrementsVersionAndKeepsLifecycleTimestampsAcrossStatusChanges() {
		HoriTipRecord created = repository.insertDraft(new NewHoriTip(
			"TIP_STATUS",
			draft(List.of(firstPlace), true),
			"admin",
			NOW)).orElseThrow();

		HoriTipRecord active = repository.updateStatus(
			created.id(), HoriTipStatus.ACTIVE, "operator", NOW.plusSeconds(1));
		HoriTipRecord inactive = repository.updateStatus(
			created.id(), HoriTipStatus.INACTIVE, "operator", NOW.plusSeconds(2));
		HoriTipRecord archived = repository.updateStatus(
			created.id(), HoriTipStatus.ARCHIVED, "admin", NOW.plusSeconds(3));

		assertEquals(2, active.version());
		assertEquals(NOW.plusSeconds(1), active.activatedAt());
		assertEquals(3, inactive.version());
		assertEquals(active.activatedAt(), inactive.activatedAt());
		assertEquals(4, archived.version());
		assertEquals(NOW.plusSeconds(3), archived.archivedAt());
		assertEquals("admin", archived.updatedBySubject());
	}

	private HoriTipDraft draft(List<Long> destinationIds, boolean completeTranslations) {
		List<HoriTipTranslation> translations = completeTranslations
			? List.of(
				new HoriTipTranslation(PlaceLanguage.KO, "Korean body"),
				new HoriTipTranslation(PlaceLanguage.EN, "English body"))
			: List.of(new HoriTipTranslation(PlaceLanguage.KO, "Korean body"));
		return new HoriTipDraft(
			HoriTipPlacement.AFTER_SEGMENT,
			100,
			new HoriTipScope(HoriTipScopeType.DESTINATION_PLACES, destinationIds),
			new HoriTipTrigger(
				List.of(HoriTipRouteMode.TRAIN),
				List.of("KTX"),
				List.of(),
				List.of("Gimcheon"),
				3600,
				null,
				null),
			translations,
			NOW.minusSeconds(60),
			NOW.plusSeconds(3600),
			"Operator note");
	}

	private long place(String ktoContentId, boolean visible) {
		jdbcTemplate.update(
			"""
			INSERT INTO places
			    (kto_content_id, service_region_code, show_flag, active, data_quality_score)
			VALUES (?, 'SEOUL', ?, TRUE, 90.00)
			""",
			ktoContentId,
			visible);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = ?",
			Long.class,
			ktoContentId);
	}

	private int count(String table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}
}
