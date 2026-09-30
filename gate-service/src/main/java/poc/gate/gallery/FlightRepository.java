package poc.gate.gallery;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data generates the implementation at startup: findById, save, delete, ... for free. */
public interface FlightRepository extends JpaRepository<Flight, String> {
}
