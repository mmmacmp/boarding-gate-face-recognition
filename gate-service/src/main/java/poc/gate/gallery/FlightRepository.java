package poc.gate.gallery;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data generates the implementation at startup: findById, save, delete, ... for free. */
public interface FlightRepository extends JpaRepository<Flight, String> {

    /** Derived query: Spring builds "WHERE status = ? AND departed_at < ?" from the method name. */
    List<Flight> findByStatusAndDepartedAtBefore(Flight.Status status, Instant cutoff);
}
