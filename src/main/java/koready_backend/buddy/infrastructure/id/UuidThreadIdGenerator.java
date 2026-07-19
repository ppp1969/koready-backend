package koready_backend.buddy.infrastructure.id;

import java.util.UUID;

import org.springframework.stereotype.Component;

import koready_backend.buddy.application.port.ThreadIdGenerator;

@Component
public class UuidThreadIdGenerator implements ThreadIdGenerator {

	@Override
	public String nextId() {
		return "thread_" + UUID.randomUUID().toString().replace("-", "");
	}
}
