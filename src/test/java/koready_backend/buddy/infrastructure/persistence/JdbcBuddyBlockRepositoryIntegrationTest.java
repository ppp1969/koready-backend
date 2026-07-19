package koready_backend.buddy.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import koready_backend.buddy.application.port.BuddyBlockRepository;
import koready_backend.buddy.application.port.BuddyBlockRepository.BlockRelationship;
import koready_backend.buddy.application.port.BuddyMessageRepository;
import koready_backend.buddy.application.port.BuddyMessageRepository.MessageThread;
import koready_backend.buddy.application.port.BuddyMessageRepository.StoredMessage;
import koready_backend.buddy.application.port.BuddyReportRepository;
import koready_backend.buddy.application.port.BuddyReportRepository.NewReport;
import koready_backend.buddy.application.port.BuddyReportRepository.StoredReport;
import koready_backend.buddy.domain.ReportStatus;
import koready_backend.buddy.domain.ReportTargetType;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class JdbcBuddyBlockRepositoryIntegrationTest {

	private static final Instant FIRST = Instant.parse("2026-07-19T04:00:00Z");
	private static final Instant SECOND = Instant.parse("2026-07-19T05:00:00Z");

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private BuddyBlockRepository repository;

	@Autowired
	private BuddyReportRepository reportRepository;

	@Autowired
	private BuddyMessageRepository messageRepository;

	@Test
	void storesOneDirectionalBlockAndPreservesTheFirstTimestamp() {
		long blocker = user("usr_blocker_db");
		long blocked = user("usr_blocked_db");
		long profileId = profile(blocked, "Blocked Buddy");

		Instant first = repository.block(blocker, blocked, FIRST);
		Instant repeated = repository.block(blocker, blocked, SECOND);

		assertEquals(FIRST, first);
		assertEquals(FIRST, repeated);
		assertEquals(1, blockCount(blocker, blocked));
		assertEquals(new BlockRelationship(true, false),
			repository.relationship(blocker, blocked));
		assertEquals(new BlockRelationship(false, true),
			repository.relationship(blocked, blocker));
		assertEquals(blocked,
			repository.findActiveProfileOwnerId(profileId).orElseThrow());
		assertTrue(repository.findActiveProfileOwnerId(999_999L).isEmpty());
	}

	@Test
	void resolvesOnlyActiveUsersAndUnblocksIdempotently() {
		long blocker = user("usr_blocker_active");
		long blocked = user("usr_blocked_active");
		long deleted = user("usr_blocker_deleted");
		long deletedProfileId = profile(deleted, "Deleted Buddy");
		jdbcTemplate.update("UPDATE users SET deleted_at = NOW(6) WHERE id = ?", deleted);
		repository.block(blocker, blocked, FIRST);

		assertEquals(blocker,
			repository.findActiveUserId("usr_blocker_active").orElseThrow());
		assertTrue(repository.findActiveUserId("usr_blocker_deleted").isEmpty());
		assertTrue(repository.findActiveUserId("usr_missing").isEmpty());
		assertTrue(repository.findActiveProfileOwnerId(deletedProfileId).isEmpty());

		repository.unblock(blocker, blocked);
		repository.unblock(blocker, blocked);

		assertEquals(0, blockCount(blocker, blocked));
	}

	@Test
	void databaseRejectsSelfBlocksAndUnknownUsers() {
		long userId = user("usr_block_constraint");

		assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
			"""
			INSERT INTO buddy_blocks (blocker_user_id, blocked_user_id, created_at)
			VALUES (?, ?, NOW(6))
			""",
			userId,
			userId));
		assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
			"""
			INSERT INTO buddy_blocks (blocker_user_id, blocked_user_id, created_at)
			VALUES (?, ?, NOW(6))
			""",
			userId,
			Long.MAX_VALUE));
	}

	@Test
	void flywayCreatesTheBuddyReportSafetySchema() {
		Integer tableCount = jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*)
			FROM information_schema.tables
			WHERE table_schema = DATABASE() AND table_name = 'buddy_reports'
			""",
			Integer.class);
		Integer idempotencyIndexCount = jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*)
			FROM information_schema.statistics
			WHERE table_schema = DATABASE()
			  AND table_name = 'buddy_reports'
			  AND index_name = 'uq_buddy_report_idempotency'
			""",
			Integer.class);

		assertEquals(1, tableCount);
		assertEquals(2, idempotencyIndexCount);
	}

	@Test
	void storesProfileAndReceivedMessageReportsIdempotently() {
		long reporterUserId = user("usr_report_db_reporter");
		long targetUserId = user("usr_report_db_target");
		long reporterProfileId = profile(reporterUserId, "Reporter");
		long targetProfileId = profile(targetUserId, "Target");
		long placeId = place("REPORT-PLACE-001", "Report Place");
		MessageThread thread = messageRepository.findOrCreateThread(
			placeId,
			reporterProfileId,
			targetProfileId,
			"thread_report_db_001",
			FIRST);
		StoredMessage received = messageRepository.appendMessage(
			thread,
			targetProfileId,
			reporterProfileId,
			"Evidence message",
			FIRST);

		assertEquals(reporterUserId,
			reportRepository.findActiveReporterUserId("usr_report_db_reporter")
				.orElseThrow());
		assertEquals(targetUserId,
			reportRepository.findActiveProfileTarget(targetProfileId)
				.orElseThrow().ownerUserId());
		assertEquals(targetProfileId,
			reportRepository.findReceivedMessageTarget(
				received.messageId(), reporterUserId).orElseThrow().senderProfileId());
		assertTrue(reportRepository.findReceivedMessageTarget(
			received.messageId(), targetUserId).isEmpty());

		NewReport profileReport = new NewReport(
			reporterUserId,
			ReportTargetType.PROFILE,
			targetProfileId,
			null,
			"Impersonation",
			"report-db-key-001",
			"a".repeat(64),
			FIRST);
		StoredReport first = reportRepository.save(profileReport);
		StoredReport replay = reportRepository.save(profileReport);
		StoredReport conflictingReplay = reportRepository.save(new NewReport(
			reporterUserId,
			ReportTargetType.PROFILE,
			targetProfileId,
			null,
			"Changed reason",
			"report-db-key-001",
			"f".repeat(64),
			SECOND));
		assertEquals(first, replay);
		assertEquals(first, conflictingReplay);
		assertEquals(ReportStatus.RECEIVED, first.status());
		assertEquals(Long.toString(targetProfileId), first.targetId());

		StoredReport messageReport = reportRepository.save(new NewReport(
			reporterUserId,
			ReportTargetType.MESSAGE,
			targetProfileId,
			received.messageId(),
			"Abusive language",
			"report-db-key-002",
			"b".repeat(64),
			SECOND));
		assertEquals(Long.toString(received.messageId()), messageReport.targetId());
		assertEquals(2, jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM buddy_reports WHERE reporter_user_id = ?",
			Integer.class,
			reporterUserId));

		jdbcTemplate.update(
			"UPDATE users SET deleted_at = NOW(6) WHERE id = ?", targetUserId);
		assertTrue(reportRepository.findActiveProfileTarget(targetProfileId).isEmpty());
		assertTrue(reportRepository.findReceivedMessageTarget(
			received.messageId(), reporterUserId).isPresent());
	}

	@Test
	void databaseRejectsInvalidReportTargetsReasonsAndReferences() {
		long reporterUserId = user("usr_report_db_constraint");
		long targetUserId = user("usr_report_db_constraint_target");
		long targetProfileId = profile(targetUserId, "Constraint Target");

		assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
			"""
			INSERT INTO buddy_reports
			    (reporter_user_id, target_type, target_profile_id,
			     target_message_id, reason, status, idempotency_key,
			     request_hash, created_at, updated_at)
			VALUES (?, 'MESSAGE', ?, NULL, 'Reason', 'RECEIVED',
			        'constraint-key-001', ?, NOW(6), NOW(6))
			""",
			reporterUserId,
			targetProfileId,
			"c".repeat(64)));
		assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
			"""
			INSERT INTO buddy_reports
			    (reporter_user_id, target_type, target_profile_id,
			     target_message_id, reason, status, idempotency_key,
			     request_hash, created_at, updated_at)
			VALUES (?, 'PROFILE', ?, NULL, '   ', 'RECEIVED',
			        'constraint-key-002', ?, NOW(6), NOW(6))
			""",
			reporterUserId,
			targetProfileId,
			"d".repeat(64)));
		assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
			"""
			INSERT INTO buddy_reports
			    (reporter_user_id, target_type, target_profile_id,
			     target_message_id, reason, status, idempotency_key,
			     request_hash, created_at, updated_at)
			VALUES (?, 'MESSAGE', ?, ?, 'Reason', 'RECEIVED',
			        'constraint-key-003', ?, NOW(6), NOW(6))
			""",
			reporterUserId,
			targetProfileId,
			Long.MAX_VALUE,
			"e".repeat(64)));
	}

	private long user(String publicId) {
		jdbcTemplate.update(
			"INSERT INTO users (public_id, signup_status) VALUES (?, 'COMPLETED')",
			publicId);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM users WHERE public_id = ?", Long.class, publicId);
	}

	private long profile(long userId, String nickname) {
		jdbcTemplate.update(
			"""
			INSERT INTO buddy_profiles
			    (user_id, nickname, korean_level, profile_public, sns_public,
			     allows_messages, created_at, updated_at)
			VALUES (?, ?, 'BEGINNER', TRUE, FALSE, TRUE, NOW(6), NOW(6))
			""",
			userId,
			nickname);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM buddy_profiles WHERE user_id = ?", Long.class, userId);
	}

	private int blockCount(long blockerUserId, long blockedUserId) {
		return jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*) FROM buddy_blocks
			WHERE blocker_user_id = ? AND blocked_user_id = ?
			""",
			Integer.class,
			blockerUserId,
			blockedUserId);
	}

	private long place(String contentId, String title) {
		jdbcTemplate.update(
			"INSERT INTO places (kto_content_id, active, show_flag) VALUES (?, TRUE, TRUE)",
			contentId);
		long placeId = jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = ?", Long.class, contentId);
		jdbcTemplate.update(
			"""
			INSERT INTO place_localizations
			    (place_id, language, title, translation_source)
			VALUES (?, 'KO', ?, 'MANUAL_EDITED')
			""",
			placeId,
			title);
		return placeId;
	}
}
