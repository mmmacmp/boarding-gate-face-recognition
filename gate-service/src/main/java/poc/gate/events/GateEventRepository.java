package poc.gate.events;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GateEventRepository extends JpaRepository<GateEvent, Long> {

    Optional<GateEvent> findByIdempotencyKey(String idempotencyKey);
}
