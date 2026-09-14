package koready_backend.editorial.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EditorialSourceComparisonTest {

	@Test
	void separatesClassificationOnlyChangesFromContentChanges() {
		var before = new EditorialSourceSnapshot(
			"장소", "Place", "서울", "설명", "CULTURE_EXPERIENCE", "fact");

		var classification = EditorialSourceComparison.compare(before,
			new EditorialSourceSnapshot(
				"장소", "Place", "서울", "설명", "DRAMA_LOCATION", "fact"));
		assertEquals(EditorialSourceChangeType.CLASSIFICATION_CHANGED,
			classification.type());
		assertEquals("travelStyles", classification.changes().getFirst().field());

		var content = EditorialSourceComparison.compare(before,
			new EditorialSourceSnapshot(
				"장소", "Place", "서울", "새 설명", "DRAMA_LOCATION", "fact"));
		assertEquals(EditorialSourceChangeType.CONTENT_CHANGED, content.type());
	}
}
