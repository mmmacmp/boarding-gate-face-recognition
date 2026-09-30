package poc.gate.api;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import poc.gate.clients.DcsClient;
import poc.gate.gallery.Flight;
import poc.gate.gallery.FlightRepository;
import poc.gate.gallery.GalleryEntryRepository;
import poc.gate.gallery.PassengerConsent;
import poc.gate.gallery.PassengerConsentRepository;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * DEMO ONLY: the X-ray console's view of a flight's gallery. The bean only exists when demo.xray=true
 * (@ConditionalOnProperty), so with the default (false) these URLs do not exist and return 404.
 * Pseudonymous ids and statuses only: no images, no embeddings (the gate-service never serves face images).
 */
@RestController
@ConditionalOnProperty(name = "demo.xray", havingValue = "true")
public class DemoController {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Entry(String profileId, String status, boolean isSynthetic) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Breaker(int minimumCalls, int windowSize, int halfOpenCalls) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Gallery(String flightId, Flight.Status flightStatus, List<Entry> entries, Breaker identityBreaker) {
    }

    private final DcsClient dcs;
    private final FlightRepository flights;
    private final GalleryEntryRepository entries;
    private final PassengerConsentRepository consents;
    private final CircuitBreakerRegistry breakers;
    private final String syntheticPrefix;

    public DemoController(DcsClient dcs, FlightRepository flights, GalleryEntryRepository entries,
                          PassengerConsentRepository consents, CircuitBreakerRegistry breakers,
                          @Value("${demo.synthetic-id-prefix:S}") String syntheticPrefix) {
        this.dcs = dcs;
        this.flights = flights;
        this.entries = entries;
        this.consents = consents;
        this.breakers = breakers;
        this.syntheticPrefix = syntheticPrefix;
    }

    /** Every manifest passenger with the state the database has right now. */
    @GetMapping("/v1/demo/flights/{flightId}/gallery")
    public Gallery gallery(@PathVariable String flightId) {
        Set<String> boarded = entries.findBoardedProfileIds(flightId);
        Set<String> active = entries.findActiveProfileIds(flightId);
        List<Entry> list = dcs.manifest(flightId).passengers().stream().map(p -> {
            String id = p.profileId();
            boolean consented = consents.findById(id).map(PassengerConsent::isActive).orElse(false);
            String status = boarded.contains(id) ? "BOARDED"
                    : !consented ? "CONSENT_WITHDRAWN"
                    : active.contains(id) ? "ACTIVE"
                    : "NOT_IN_GALLERY"; // e.g. purged after departure
            return new Entry(id, status, id.startsWith(syntheticPrefix));
        }).toList();
        CircuitBreakerConfig c = breakers.circuitBreaker("identity").getCircuitBreakerConfig();
        return new Gallery(flightId, flights.findById(flightId).map(Flight::getStatus).orElse(null), list,
                new Breaker(c.getMinimumNumberOfCalls(), c.getSlidingWindowSize(),
                        c.getPermittedNumberOfCallsInHalfOpenState()));
    }

    /** Demo reset only: forget all breaker history so every rehearsal starts CLOSED. */
    @PostMapping("/v1/demo/breakers/reset")
    public void resetBreakers() {
        breakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }
}
