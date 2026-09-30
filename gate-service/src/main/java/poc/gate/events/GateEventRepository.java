package poc.gate.events;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GateEventRepository extends JpaRepository<GateEvent, Long> {
}
