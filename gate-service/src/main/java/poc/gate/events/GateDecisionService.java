package poc.gate.events;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public GateDecisionService(FlightRepository flights, ModelClient model, GalleryService gallery,
                               DecisionPolicy policy, IdentityClient identity, BoardingWriter writer) {
        this.flights = flights;
        this.model = model;
        this.gallery = gallery;
        this.policy = policy;
        this.identity = identity;
        this.writer = writer;
    }

    public GateEvent handle(String gateId, String flightId, String idempotencyKey, byte[] image) {
        Decision decision;
        try {
            decision = decide(flightId, image);
        } catch (DependencyUnavailableException e) {
            decision = Decision.manual(e.reason());
        } catch (RuntimeException e) {
            log.warn("gate_event_error gate={} flight={} error={}", gateId, flightId, e.getClass().getSimpleName());
            decision = Decision.manual(ReasonCode.INTERNAL_ERROR);
        }
        GateEvent event = writer.record(idempotencyKey, gateId, flightId, decision);
        // One structured line per decision. Never images or embeddings; the identity only for BOARD.
        log.info("gate_decision gate={} flight={} decision={} reason={} top1={} top2={} profile={}",
                gateId, flightId, event.getOutcome(), event.getReason(), round(event.getTop1()),
                round(event.getTop2()), event.getProfileId() == null ? "-" : event.getProfileId());
        return event;
    }

    private Decision decide(String flightId, byte[] image) {
        Flight flight = flights.findById(flightId).orElse(null);
        if (flight == null || flight.getStatus() != Flight.Status.BOARDING) {
            return Decision.manual(ReasonCode.GALLERY_UNAVAILABLE);
        }
        ModelClient.EmbedResponse probe = model.embed(image); // the probe lives in memory only, for this call
        if (!probe.modelId().equals(flight.getModelId())) {
            return Decision.manual(ReasonCode.MODEL_MISMATCH); // never compare templates from different models
        }
        List<Candidate> top2 = probe.faceCount() == 1
                ? gallery.searchTop2(flight, probe.modelId(), probe.embedding())
                : List.of();
        Decision decision = policy.decide(probe.faceCount(), probe.modelId(), top2);
        if (decision.isBoard() && !identity.isValid(decision.profileId(), flightId)) {
            return decision.toManual(ReasonCode.IDP_REJECTED);
        }
        return decision;
    }

    private static String round(Double score) {
        return score == null ? "-" : String.format("%.3f", score);
    }
}
