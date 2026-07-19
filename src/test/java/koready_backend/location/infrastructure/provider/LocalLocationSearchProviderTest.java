package koready_backend.location.infrastructure.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LocalLocationSearchProviderTest {

	private final LocalLocationSearchProvider provider = new LocalLocationSearchProvider();

	@Test
	void searchesNamesAndAddressesWithoutExternalCredentials() {
		var school = provider.search("성신 여자 대학교", 10);
		var region = provider.search("전북특별자치도", 10);

		assertEquals(2, school.size());
		assertEquals("성신여자대학교", school.getFirst().name());
		assertEquals(1, region.size());
		assertEquals("전주한옥마을", region.getFirst().name());
	}

	@Test
	void returnsAnEmptyListForUnknownQueries() {
		assertTrue(provider.search("없는 위치", 10).isEmpty());
	}
}
