package poc.gate.events;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Wall-clock milliseconds per stage of one gate event, for the console's latency waterfall. A plain data holder
 * (like a Python dataclass): it is filled in stage by stage, so public fields instead of a record; Jackson writes
 * public fields as JSON. A stage that never ran stays null. Returned in the response, never stored.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class StageTimings {

    public Double embedMs;
    public Double searchMs;
    public Double identityMs;
    public Double dbMs;

    static double since(long startNanos) {
        return Math.round((System.nanoTime() - startNanos) / 10_000.0) / 100.0; // ms, 2 decimals
    }
}
