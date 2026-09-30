package poc.gate.events;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GateEventRepository extends JpaRepository<GateEvent, Long> {

    Optional<GateEvent> findByIdempotencyKey(String idempotencyKey);

    List<GateEvent> findTop20ByOrderByIdDesc(); // derived query: newest 20 events
}
