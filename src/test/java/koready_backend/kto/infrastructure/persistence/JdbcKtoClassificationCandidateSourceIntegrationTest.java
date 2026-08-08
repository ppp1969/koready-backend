package koready_backend.kto.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

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

import koready_backend.kto.application.model.KtoClassificationCandidate;
import koready_backend.kto.application.port.KtoClassificationCandidateSource;
import koready_backend.place.domain.TravelStyle;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class JdbcKtoClassificationCandidateSourceIntegrationTest {

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private KtoClassificationCandidateSource source;

	@Test
	void readsKtoCandidatesInIdOrderWithoutChangingStoredData() {
		long first = place(
			"kto-food",
			"39",
			"FD",
			"FD01",
			"FD010100",
			true,
			true,
			"https://images.example/food.jpg");
		localize(first, "국문 음식점");
		style(first, TravelStyle.DRAMA_LOCATION, "MANUAL");
		style(first, TravelStyle.LOCAL_FOOD, "LCLS");

		long second = place(
			"kto-nature",
			"12",
			"NA",
			"NA01",
			"NA010100",
			true,
			false,
			null);
		localize(second, "국문 자연명소");
		image(second);

		int placeCountBefore = count("places");
		int styleCountBefore = count("place_style_mappings");

		List<KtoClassificationCandidate> firstPage = source.findAfter(0L, 1);
		List<KtoClassificationCandidate> secondPage = source.findAfter(first, 10);

		assertEquals(List.of(first), firstPage.stream().map(
			KtoClassificationCandidate::placeId).toList());
		assertEquals(Set.of(TravelStyle.DRAMA_LOCATION), firstPage.getFirst().manualStyles());
		assertTrue(firstPage.getFirst().hasImage());
		assertTrue(firstPage.getFirst().currentlyPublished());
		assertEquals("국문 음식점", firstPage.getFirst().title());

		assertEquals(List.of(second), secondPage.stream().map(
			KtoClassificationCandidate::placeId).toList());
		assertTrue(secondPage.getFirst().hasImage());
		assertFalse(secondPage.getFirst().currentlyPublished());
		assertEquals(Set.of(), secondPage.getFirst().manualStyles());

		assertEquals(placeCountBefore, count("places"));
		assertEquals(styleCountBefore, count("place_style_mappings"));
	}

	private long place(
		String contentId,
		String contentTypeId,
		String level1,
		String level2,
		String level3,
		boolean active,
		boolean showFlag,
		String firstImageUrl
	) {
		jdbcTemplate.update(
			"""
			INSERT INTO places
			    (kto_content_id, kto_content_type_id,
			     lcls_systm1, lcls_systm2, lcls_systm3,
			     active, show_flag, first_image_url)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?)
			""",
			contentId,
			contentTypeId,
			level1,
			level2,
			level3,
			active,
			showFlag,
			firstImageUrl);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = ?",
			Long.class,
			contentId);
	}

	private void localize(long placeId, String title) {
		jdbcTemplate.update(
			"""
			INSERT INTO place_localizations
			    (place_id, language, title, translation_source)
			VALUES (?, 'KO', ?, 'KTO_KO')
			""",
			placeId,
			title);
	}

	private void style(long placeId, TravelStyle style, String source) {
		jdbcTemplate.update(
			"""
			INSERT INTO place_style_mappings
			    (place_id, travel_style, source, confidence)
			VALUES (?, ?, ?, 1.0000)
			""",
			placeId,
			style.name(),
			source);
	}

	private void image(long placeId) {
		jdbcTemplate.update(
			"""
			INSERT INTO place_images
			    (place_id, image_url, image_url_sha256, source_type,
			     source_priority, source_order)
			VALUES (?, 'https://images.example/detail.jpg',
			        REPEAT('a', 64), 'KTO_DETAIL', 100, 1)
			""",
			placeId);
	}

	private int count(String table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}
}
