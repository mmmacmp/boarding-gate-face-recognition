package poc.gate.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;
import poc.gate.events.GateEvent;

/**
 * Micrometer metrics (like prometheus_client), exported at /actuator/prometheus. Micrometer names use dots;
 * the Prometheus exporter turns "gate.decisions" into gate_decisions_total and "gate.decision" into
 * gate_decision_seconds. Labels are low-cardinality only: never a profile id.
 */
@Component
public class GateMetrics {

    private final MeterRegistry registry;
    private final Timer latency;
    private final DistributionSummary top1;
    private final DistributionSummary margin;

    public GateMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.latency = Timer.builder("gate.decision").description("Gate decision latency").register(registry);
        this.top1 = DistributionSummary.builder("gate.match.top1.score").register(registry);
        this.margin = DistributionSummary.builder("gate.match.margin").register(registry);
    }

    public void record(GateEvent event, Duration elapsed) {
        Counter.builder("gate.decisions")
                .tag("gate", event.getGateId())
                .tag("decision", event.getOutcome().name())
                .tag("reason", event.getReason().name())
                .register(registry)   // returns the existing counter if these tags were seen before
                .increment();
        latency.record(elapsed);
        if (event.getTop1() != null) {
            top1.record(event.getTop1());    // drift in these distributions is a proxy for camera or model problems
            if (event.getTop2() != null) {
                margin.record(event.getTop1() - event.getTop2());
            }
        }
    }
}
