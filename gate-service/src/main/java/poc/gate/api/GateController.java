package poc.gate.api;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import poc.gate.config.GateProperties;
import poc.gate.decision.Decision;
import poc.gate.decision.ReasonCode;
import poc.gate.events.GateDecisionService;
import poc.gate.events.GateEvent;
import poc.gate.events.GateEventRepository;
import poc.gate.events.StageTimings;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** A gate camera posts one capture per passenger. Like a FastAPI router function with File/Form/Header params. */
@RestController
public class GateController {

    /** timings is only present in the response to the request that made the decision (it is not stored). */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record GateEventResponse(long eventId, String gateId, String flightId, Decision.Outcome decision,
                                    ReasonCode reason, String profileId, Double top1, Double top2,
                                    Instant createdAt, StageTimings timings) {

        static GateEventResponse from(GateEvent e) {
            return new GateEventResponse(e.getId(), e.getGateId(), e.getFlightId(), e.getOutcome(), e.getReason(),
                    e.getProfileId(), e.getTop1(), e.getTop2(), e.getCreatedAt(), e.getTimings());
        }
    }

    private final GateDecisionService service;
    private final GateEventRepository events;
    private final GateProperties props;

    public GateController(GateDecisionService service, GateEventRepository events, GateProperties props) {
        this.service = service;
        this.events = events;
        this.props = props;
    }

    @PostMapping(path = "/v1/gates/{gateId}/events", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public GateEventResponse event(@PathVariable String gateId,
                                   @RequestParam("flight_id") String flightId,
                                   @RequestHeader("Idempotency-Key") String idempotencyKey,
                                   @RequestPart("image") MultipartFile image) throws IOException {
        // The image bytes stay in memory for this request only; nothing writes them to disk or logs.
        return GateEventResponse.from(service.handle(gateId, flightId, idempotencyKey, image.getBytes()));
    }

    /** Newest 20 decisions for the operations console (profile_id is only ever stored for BOARD). */
    @GetMapping("/v1/events")
    public List<GateEventResponse> recent() {
        return events.findTop20ByOrderByIdDesc().stream().map(GateEventResponse::from).toList();
    }

    /** Thresholds per model and the margin, so the console can draw them next to the scores. */
    @GetMapping("/v1/policy")
    public GateProperties.Policy policy() {
        return props.policy();
    }
}
