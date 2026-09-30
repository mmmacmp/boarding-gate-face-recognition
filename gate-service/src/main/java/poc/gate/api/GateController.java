package poc.gate.api;

import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import poc.gate.decision.Decision;
import poc.gate.decision.ReasonCode;
import poc.gate.events.GateDecisionService;
import poc.gate.events.GateEvent;

/** A gate camera posts one capture per passenger. Like a FastAPI router function with File/Form/Header params. */
@RestController
public class GateController {

    public record GateEventResponse(long eventId, String gateId, String flightId, Decision.Outcome decision,
                                    ReasonCode reason, String profileId, Double top1, Double top2) {

        static GateEventResponse from(GateEvent e) {
            return new GateEventResponse(e.getId(), e.getGateId(), e.getFlightId(), e.getOutcome(), e.getReason(),
                    e.getProfileId(), e.getTop1(), e.getTop2());
        }
    }

    private final GateDecisionService service;

    public GateController(GateDecisionService service) {
        this.service = service;
    }

    @PostMapping(path = "/v1/gates/{gateId}/events", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public GateEventResponse event(@PathVariable String gateId,
                                   @RequestParam("flight_id") String flightId,
                                   @RequestHeader("Idempotency-Key") String idempotencyKey,
                                   @RequestPart("image") MultipartFile image) throws IOException {
        // The image bytes stay in memory for this request only; nothing writes them to disk or logs.
        return GateEventResponse.from(service.handle(gateId, flightId, idempotencyKey, image.getBytes()));
    }
}
