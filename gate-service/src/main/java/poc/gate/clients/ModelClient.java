package poc.gate.clients;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import poc.gate.config.GateProperties;
import poc.gate.decision.ReasonCode;

/** Calls the stateless model-service. It never sends an identity, only image bytes. */
@Component
public class ModelClient {

    public record Face(List<Double> bbox, double detScore, float[] embedding) {
    }

    public record EmbedResponse(String modelId, int dim, List<Face> faces) {

        public int faceCount() {
            return faces.size();
        }

        /** The model-service only returns an embedding when there is exactly one face. */
        public float[] embedding() {
            return faces.size() == 1 ? faces.get(0).embedding() : null;
        }
    }

    private final RestClient http;

    public ModelClient(RestClient.Builder builder, GateProperties props) {
        this.http = RestClients.create(builder, props.modelService());
    }

    /**
     * The breaker is applied by a Spring AOP proxy around this bean: callers get the proxy, which counts failures
     * and slow calls, and while OPEN rejects calls instantly without any HTTP request.
     */
    @CircuitBreaker(name = "model", fallbackMethod = "unavailable")
    public EmbedResponse embed(byte[] image) {
        return http.post()
                .uri("/v1/embed")
                .contentType(MediaType.IMAGE_JPEG)
                .body(image)
                .retrieve()               // throws for 4xx/5xx; timeouts and refused connections throw too
                .body(EmbedResponse.class);
    }

    /** Fallback: same parameters plus the Throwable (an HTTP error, a timeout, or CallNotPermittedException). */
    private EmbedResponse unavailable(byte[] image, Throwable cause) {
        throw new DependencyUnavailableException(ReasonCode.MODEL_UNAVAILABLE, cause);
    }
}
