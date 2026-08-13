package koready_backend.editorial.domain;

public enum EditorialJobPriority {
	HIGH(100),
	NORMAL(50);

	private final int weight;

	EditorialJobPriority(int weight) {
		this.weight = weight;
	}

	public int weight() {
		return weight;
	}
}
