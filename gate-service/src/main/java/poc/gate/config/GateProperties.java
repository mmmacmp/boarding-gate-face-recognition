package poc.gate.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The "gate:" section of application.yml, bound into immutable records at startup (like a Pydantic Settings
 * model). Spring converts "800ms" into a Duration and the YAML map into a Map.
 */
@ConfigurationProperties("gate")
public record GateProperties(Endpoint modelService, Endpoint identity, Endpoint dcs, Policy policy, Kek kek,
                             Retention retention) {

    public record Retention(Duration grace, Duration purgeInterval) {
    }

    public record Endpoint(String url, Duration connectTimeout, Duration readTimeout) {
    }

    public record Policy(Map<String, Double> thresholds, double margin) {
    }

    public record Kek(String path, String password) {
    }
}
