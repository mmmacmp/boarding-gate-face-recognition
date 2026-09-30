package poc.gate.events;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import java.time.Instant;
import poc.gate.decision.Decision;
import poc.gate.decision.ReasonCode;

/** The stored result of one gate event. No image, no embedding; profile_id only for BOARD. */
@Entity
public class GateEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String idempotencyKey;
    private String gateId;
    private String flightId;
    @Enumerated(EnumType.STRING)
    private Decision.Outcome outcome;
    @Enumerated(EnumType.STRING)
    private ReasonCode reason;
    private String profileId;
    private Double top1;
    private Double top2;
    private Instant createdAt;
    @Transient // not a column: only lives in memory for the response of the request that created the event
    private StageTimings timings;

    protected GateEvent() {
    }

    public GateEvent(String idempotencyKey, String gateId, String flightId, Decision decision) {
        this.idempotencyKey = idempotencyKey;
        this.gateId = gateId;
        this.flightId = flightId;
        this.outcome = decision.outcome();
        this.reason = decision.reason();
        this.profileId = decision.isBoard() ? decision.profileId() : null;
        this.top1 = decision.top1();
        this.top2 = decision.top2();
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getGateId() {
        return gateId;
    }

    public String getFlightId() {
        return flightId;
    }

    public Decision.Outcome getOutcome() {
        return outcome;
    }

    public ReasonCode getReason() {
        return reason;
    }

    public String getProfileId() {
        return profileId;
    }

    public Double getTop1() {
        return top1;
    }

    public Double getTop2() {
        return top2;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public StageTimings getTimings() {
        return timings;
    }

    void setTimings(StageTimings timings) {
        this.timings = timings;
    }
}
