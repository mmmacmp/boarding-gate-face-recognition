package poc.gate.events;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import poc.gate.clients.DependencyUnavailableException;
import poc.gate.clients.IdentityClient;
import poc.gate.clients.ModelClient;
import poc.gate.decision.Candidate;
import poc.gate.decision.Decision;
import poc.gate.decision.DecisionPolicy;
import poc.gate.decision.ReasonCode;
import poc.gate.gallery.Flight;
import poc.gate.gallery.FlightRepository;
import poc.gate.gallery.GalleryService;
import poc.gate.metrics.GateMetrics;

/**
 * One gate event, end to end. Fail-safe: any failure becomes MANUAL with a reason code, never an error that
 * blocks the gate. All HTTP calls happen here, BEFORE the single database transaction in BoardingWriter.
 */
@Service
public class GateDecisionService {

    private static final Logger log = LoggerFactory.getLogger(GateDecisionService.class);

    private final FlightRepository flights;
    private final ModelClient model;
    private final GalleryService gallery;
    private final DecisionPolicy policy;
    private final IdentityClient identity;
    private final BoardingWriter writer;
    private final GateEventRepository events;
    private final GateMetrics metrics;

    public GateDecisionService(FlightRepository flights, ModelClient model, GalleryService gallery,
                               DecisionPolicy policy, IdentityClient identity, BoardingWriter writer,
                               GateEventRepository events, GateMetrics metrics) {
        this.flights = flights;
        this.model = model;
        this.gallery = gallery;
        this.policy = policy;
        this.identity = identity;
        this.writer = writer;
        this.events = events;
        this.metrics = metrics;
    }

    public GateEvent handle(String gateId, String flightId, String idempotencyKey, byte[] image) {
        // Idempotency: a retried request (same key, e.g. after a network timeout) gets the stored decision back.
        Optional<GateEvent> previous = events.findByIdempotencyKey(idempotencyKey);
        if (previous.isPresent()) {
            return previous.get();
        }
        long start = System.nanoTime();
        StageTimings timings = new StageTimings();
        Decision decision;
        try {
            decision = decide(flightId, image, timings);
        } catch (DependencyUnavailableException e) {
            decision = Decision.manual(e.reason());
        } catch (RuntimeException e) {
            log.warn("gate_event_error gate={} flight={} error={}", gateId, flightId, e.getClass().getSimpleName());
            decision = Decision.manual(ReasonCode.INTERNAL_ERROR);
        }
        GateEvent event;
        long dbStart = System.nanoTime();
        try {
            event = writer.record(idempotencyKey, gateId, flightId, decision);
            timings.dbMs = StageTimings.since(dbStart);
            event.setTimings(timings);
        } catch (DataIntegrityViolationException e) {
            // The same key arrived twice at the same moment: the unique constraint let the first insert win and
            // rolled this transaction back (including any board update), so return the winner's decision.
            return events.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> e);
        }
        metrics.record(event, Duration.ofNanos(System.nanoTime() - start));
        // One structured line per decision. Never images or embeddings; the identity only for BOARD.
        log.info("gate_decision gate={} flight={} decision={} reason={} top1={} top2={} profile={}",
                gateId, flightId, event.getOutcome(), event.getReason(), round(event.getTop1()),
                round(event.getTop2()), event.getProfileId() == null ? "-" : event.getProfileId());
        return event;
    }

    private Decision decide(String flightId, byte[] image, StageTimings timings) {
        Flight flight = flights.findById(flightId).orElse(null);
        if (flight == null || flight.getStatus() != Flight.Status.BOARDING) {
            return Decision.manual(ReasonCode.GALLERY_UNAVAILABLE);
        }
        long embedStart = System.nanoTime();
        ModelClient.EmbedResponse probe;
        try {
            probe = model.embed(image); // the probe lives in memory only, for this call
        } finally {
            timings.embedMs = StageTimings.since(embedStart); // also when it failed: a timeout shows as a bar
        }
        if (probe.onlySmallFaces()) {
            return Decision.manual(ReasonCode.LOW_QUALITY); // a size-based proxy, not an image-quality model
        }
        if (!probe.modelId().equals(flight.getModelId())) {
            return Decision.manual(ReasonCode.MODEL_MISMATCH); // never compare templates from different models
        }
        List<Candidate> top2 = List.of();
        if (probe.faceCount() == 1) {
            long start = System.nanoTime();
            top2 = gallery.searchTop2(flight, probe.modelId(), probe.embedding());
            metrics.recordSearch(Duration.ofNanos(System.nanoTime() - start));
            timings.searchMs = StageTimings.since(start);
        }
        Decision decision = policy.decide(probe.faceCount(), probe.modelId(), top2);
        if (!decision.isBoard()) {
            return decision;
        }
        long identityStart = System.nanoTime();
        boolean valid;
        try {
            valid = identity.isValid(decision.profileId(), flightId);
        } finally {
            timings.identityMs = StageTimings.since(identityStart);
        }
        return valid ? decision : decision.toManual(ReasonCode.IDP_REJECTED);
    }

    private static String round(Double score) {
        return score == null ? "-" : String.format("%.3f", score);
    }
}
