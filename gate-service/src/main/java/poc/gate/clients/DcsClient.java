package poc.gate.clients;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import poc.gate.config.GateProperties;
import poc.gate.decision.ReasonCode;

/** Calls dcs-stub, the MOCK departure control system, for the flight manifest. */
@Component
public class DcsClient {

    public record Passenger(String profileId, String seat) {
    }

    public record Manifest(String flightId, Instant departureTime, List<Passenger> passengers) {
    }

    private final RestClient http;

    public DcsClient(RestClient.Builder builder, GateProperties props) {
        this.http = RestClients.create(builder, props.dcs());
    }

    public Manifest manifest(String flightId) {
        try {
            return http.get().uri("/v1/flights/{id}/manifest", flightId).retrieve().body(Manifest.class);
        } catch (RestClientException e) {
            throw new DependencyUnavailableException(ReasonCode.GALLERY_UNAVAILABLE, e);
        }
    }
}
