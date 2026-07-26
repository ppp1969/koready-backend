package koready_backend.kto.infrastructure.persistence;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import koready_backend.kto.application.port.KtoEnglishMatchCandidateSource;
import koready_backend.kto.domain.KtoEnglishMatchKeyFactory;
import koready_backend.kto.domain.KtoEnglishPlaceCandidate;

@Repository
public class JdbcKtoEnglishMatchCandidateSource implements KtoEnglishMatchCandidateSource {

	private final JdbcTemplate jdbcTemplate;

	public JdbcKtoEnglishMatchCandidateSource(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<KtoEnglishPlaceCandidate> loadCandidates() {
		return jdbcTemplate.query(
			"""
			SELECT id, kto_content_type_id, first_image_url, longitude, latitude
			FROM places
			ORDER BY id
			""",
			(rs, rowNumber) -> {
				BigDecimal longitude = rs.getBigDecimal("longitude");
				BigDecimal latitude = rs.getBigDecimal("latitude");
				return new KtoEnglishPlaceCandidate(
					rs.getLong("id"),
					KtoEnglishMatchKeyFactory.imagePathHash(rs.getString("first_image_url")),
					KtoEnglishMatchKeyFactory.koreanCoordinateContentTypeKey(
						longitude == null ? null : longitude.toPlainString(),
						latitude == null ? null : latitude.toPlainString(),
						rs.getString("kto_content_type_id")));
			}).stream()
			.filter(candidate -> candidate.imagePathHash() != null
				|| candidate.coordinateContentTypeKey() != null)
			.toList();
	}
}
