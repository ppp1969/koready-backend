package koready_backend.kto.infrastructure.snapshot;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import koready_backend.kto.application.model.KtoRawSnapshot;

final class KtoSnapshotObjectKeyFactory {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

	private KtoSnapshotObjectKeyFactory() {
	}

	static String create(KtoRawSnapshot snapshot) {
		LocalDate capturedDate = LocalDate.ofInstant(snapshot.capturedAt(), SEOUL_ZONE);
		return "kto/kor/%s/%s/event-start-%s-page-%d-%s.json.gz".formatted(
			snapshot.operation(),
			DATE.format(capturedDate),
			DATE.format(snapshot.eventStartDate()),
			snapshot.pageNumber(),
			snapshot.rawContentSha256().substring(0, 16));
	}
}
