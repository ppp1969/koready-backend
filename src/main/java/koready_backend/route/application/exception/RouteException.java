package koready_backend.route.application.exception;

public final class RouteException extends RuntimeException {

	private final Reason reason;

	public RouteException(Reason reason) {
		super(reason.message);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}

	public enum Reason {
		CONTEXT_NOT_FOUND("출발 위치 또는 목적지 정보를 확인할 수 없습니다."),
		ROUTE_NOT_FOUND("선택한 출발지에서 이 장소로 가는 대중교통 경로를 찾지 못했습니다."),
		ROUTE_NOT_AVAILABLE_AT_DEPARTURE_TIME("선택한 출발 시각에는 운행 가능한 대중교통 경로가 없습니다. 출발 시각을 변경해 주세요."),
		ROUTE_PROVIDER_UNAVAILABLE("경로 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."),
		ROUTE_EXPIRED("경로 정보가 만료되었습니다. 출발 조건으로 경로를 다시 조회해 주세요.");

		private final String message;

		Reason(String message) {
			this.message = message;
		}
	}
}
