package koready_backend.onboarding.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

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

import koready_backend.onboarding.application.CandidateSetService;
import koready_backend.onboarding.application.CandidateSetService.AdminCandidateSet;
import koready_backend.onboarding.application.CandidateSetService.CreateCandidateSetCommand;
import koready_backend.onboarding.application.CandidateSetService.CurrentCandidateSet;
import koready_backend.onboarding.application.CandidateSetService.UpdateCandidateSetCommand;
import koready_backend.onboarding.application.exception.CandidateSetNotFoundException;
import koready_backend.onboarding.domain.CandidateSetItemDraft;
import koready_backend.onboarding.domain.CandidateSetPolicyException;
import koready_backend.onboarding.domain.CandidateSetStatus;
import koready_backend.place.domain.PlaceLanguage;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class JdbcCandidateSetRepositoryIntegrationTest {

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private CandidateSetService service;

	private List<Long> readyPlaceIds;

	@BeforeEach
	void setUpReadyPlaces() {
		Integer migrationCount = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '7'",
			Integer.class);
		assertEquals(1, migrationCount);
		readyPlaceIds = IntStream.rangeClosed(1, 10)
			.mapToObj(this::insertReadyPlace)
			.toList();
	}

	@Test
	void publishesCopiesSwitchesAndArchivesImmutableCandidateSets() {
		AdminCandidateSet draft = service.createDraft(
			new CreateCandidateSetCommand("Summer curation", null),
			"42",
			true);
		assertEquals(CandidateSetStatus.DRAFT, draft.status());
		assertTrue(draft.editable());

		AdminCandidateSet updated = service.updateDraft(
			draft.candidateSetId(),
			new UpdateCandidateSetCommand("Summer curation", items(readyPlaceIds)),
			"42",
			true);
		assertEquals(10, updated.itemCount());
		assertTrue(updated.items().stream().allMatch(item -> item.placeReady()));

		AdminCandidateSet published = service.publish(
			draft.candidateSetId(), "42", true);
		assertEquals(CandidateSetStatus.PUBLISHED, published.status());
		assertTrue(published.current());
		assertFalse(published.editable());
		assertEquals(42L, published.publishedByUserId());

		CurrentCandidateSet currentEnglish = service.getCurrent(PlaceLanguage.EN);
		assertEquals(draft.candidateSetId(), currentEnglish.candidateSetId());
		assertEquals("English place 1", currentEnglish.items().getFirst().title());
		assertEquals("English message 1", currentEnglish.items().getFirst().curatorMessage());
		assertEquals(10, currentEnglish.items().size());

		AdminCandidateSet copied = service.createDraft(
			new CreateCandidateSetCommand("Autumn curation", draft.candidateSetId()),
			"84",
			true);
		assertEquals(10, copied.itemCount());
		AdminCandidateSet replacement = service.publish(
			copied.candidateSetId(), "84", true);
		assertTrue(replacement.current());
		assertFalse(service.getAdmin(draft.candidateSetId(), true).current());
		assertEquals(CandidateSetStatus.PUBLISHED,
			service.getAdmin(draft.candidateSetId(), true).status());

		AdminCandidateSet archived = service.archive(
			copied.candidateSetId(), "84", true);
		assertEquals(CandidateSetStatus.ARCHIVED, archived.status());
		assertThrows(CandidateSetNotFoundException.class,
			() -> service.getCurrent(PlaceLanguage.KO));

		Integer auditCount = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM admin_audit_logs WHERE resource_type = 'ONBOARDING_CANDIDATE_SET'",
			Integer.class);
		assertEquals(6, auditCount);
	}

	@Test
	void rejectsIncompleteAndNotReadyCandidateSetsWithoutChangingCurrentPointer() {
		AdminCandidateSet draft = service.createDraft(
			new CreateCandidateSetCommand("Invalid", null), "42", true);
		service.updateDraft(
			draft.candidateSetId(),
			new UpdateCandidateSetCommand("Invalid", items(readyPlaceIds.subList(0, 9))),
			"42",
			true);

		CandidateSetPolicyException countError = assertThrows(
			CandidateSetPolicyException.class,
			() -> service.publish(draft.candidateSetId(), "42", true));
		assertEquals(CandidateSetPolicyException.Reason.REQUIRES_TEN_ITEMS,
			countError.reason());

		jdbcTemplate.update("UPDATE places SET active = FALSE WHERE id = ?", readyPlaceIds.getLast());
		service.updateDraft(
			draft.candidateSetId(),
			new UpdateCandidateSetCommand("Invalid", items(readyPlaceIds)),
			"42",
			true);
		CandidateSetPolicyException readinessError = assertThrows(
			CandidateSetPolicyException.class,
			() -> service.publish(draft.candidateSetId(), "42", true));
		assertEquals(CandidateSetPolicyException.Reason.PLACE_NOT_READY,
			readinessError.reason());
		assertEquals(List.of(readyPlaceIds.getLast()), readinessError.placeIds());
		assertThrows(CandidateSetNotFoundException.class,
			() -> service.getCurrent(PlaceLanguage.KO));
	}

	private long insertReadyPlace(int index) {
		jdbcTemplate.update(
			"""
			INSERT INTO places
			    (kto_content_id, service_region_code, address, latitude, longitude,
			     first_image_url, show_flag, active, data_quality_score)
			VALUES (?, 'SEOUL', ?, ?, ?, ?, TRUE, TRUE, 90.00)
			""",
			"candidate-" + index,
			"Seoul address " + index,
			new BigDecimal("37.5000000"),
			new BigDecimal("127.0000000"),
			"https://example.com/place-" + index + ".jpg");
		long placeId = jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = ?",
			Long.class,
			"candidate-" + index);
		jdbcTemplate.update(
			"""
			INSERT INTO place_localizations
			    (place_id, language, title, address_text, translation_source)
			VALUES (?, 'KO', ?, ?, 'KTO_KO'),
			       (?, 'EN', ?, ?, 'KTO_EN')
			""",
			placeId,
			"Korean place " + index,
			"Seoul address " + index,
			placeId,
			"English place " + index,
			"Seoul address " + index);
		jdbcTemplate.update(
			"""
			INSERT INTO place_style_mappings (place_id, travel_style, source, confidence)
			VALUES (?, 'LOCAL_FESTIVAL', 'MANUAL', 1.0000)
			""",
			placeId);
		for (int imageOrder = 1; imageOrder <= 4; imageOrder++) {
			jdbcTemplate.update(
				"""
				INSERT INTO place_images
				    (place_id, image_url, image_url_sha256, source_type, source_priority, source_order)
				VALUES (?, ?, ?, 'KTO_DETAIL', 100, ?)
				""",
				placeId,
				"https://example.com/place-" + index + "-" + imageOrder + ".jpg",
				String.format("%064d", index * 10 + imageOrder),
				imageOrder);
		}
		return placeId;
	}

	private static List<CandidateSetItemDraft> items(List<Long> placeIds) {
		return IntStream.range(0, placeIds.size())
			.mapToObj(index -> new CandidateSetItemDraft(
				placeIds.get(index),
				index + 1,
				null,
				"Korean message " + (index + 1),
				"English message " + (index + 1),
				List.of("festival", "local"),
				"Editor note " + (index + 1)))
			.toList();
	}
}
