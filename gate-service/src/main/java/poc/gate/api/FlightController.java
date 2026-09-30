package poc.gate.api;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import poc.gate.clients.DependencyUnavailableException;
import poc.gate.gallery.Flight;
import poc.gate.gallery.FlightRepository;
import poc.gate.gallery.GalleryEntry;
import poc.gate.gallery.GalleryEntryRepository;
import poc.gate.gallery.GalleryService;

/** Flight operations for the gate agent: build the gallery before boarding, and inspect the flight. */
@RestController
public class FlightController {

    public record FlightView(String flightId, Flight.Status status, Instant departureTime, String modelId,
                             long activeEntries, long boardedEntries) {
    }

    public record BuildFailed(String flightId, String status, String reason) {
    }

    private final GalleryService gallery;
    private final FlightRepository flights;
    private final GalleryEntryRepository entries;

    public FlightController(GalleryService gallery, FlightRepository flights, GalleryEntryRepository entries) {
        this.gallery = gallery;
        this.flights = flights;
        this.entries = entries;
    }

    @PostMapping("/v1/flights/{flightId}/gallery:build")
    public ResponseEntity<?> build(@PathVariable String flightId) {
        try {
            return ResponseEntity.ok(gallery.build(flightId));
        } catch (DependencyUnavailableException e) {
            // Manifest, identity provider or model-service failed: the flight boards manually.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new BuildFailed(flightId, "GALLERY_UNAVAILABLE", e.getMessage()));
        }
    }

    @GetMapping("/v1/flights/{flightId}")
    public ResponseEntity<FlightView> get(@PathVariable String flightId) {
        return flights.findById(flightId)
                .map(f -> ResponseEntity.ok(new FlightView(f.getFlightId(), f.getStatus(), f.getDepartureTime(),
                        f.getModelId(),
                        entries.countByStatus(flightId, GalleryEntry.Status.ACTIVE.name()),
                        entries.countByStatus(flightId, GalleryEntry.Status.BOARDED.name()))))
                .orElse(ResponseEntity.notFound().build());
    }
}
