package koready_backend.kto.application.port;

public interface KtoDetailCoverageRepository {

	CoverageAggregate summarize();

	record CoverageAggregate(
		long totalPlaces,
		long completedPlaces,
		long dueForRefreshPlaces,
		ImageBuckets ktoDetailImages,
		ImageBuckets effectiveGalleryImages
	) {
	}

	record ImageBuckets(
		long zero,
		long one,
		long two,
		long three,
		long fourOrMore
	) {

		public long total() {
			return zero + one + two + three + fourOrMore;
		}

		public long lessThanFour() {
			return zero + one + two + three;
		}
	}
}
