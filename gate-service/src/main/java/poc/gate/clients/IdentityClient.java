package poc.gate.clients;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import poc.gate.config.GateProperties;
import poc.gate.decision.ReasonCode;

/** Calls mock-idp, the MOCK stand-in for an external identity provider. The real interface is not publicly known. */
@Component
public class IdentityClient {

    record VerifyRequest(String profileId, String flightId) {
    }

    record VerifyResponse(String profileId, String flightId, String result) {
    }

    private final RestClient http;

    public IdentityClient(RestClient.Builder builder, GateProperties props) {
        this.http = RestClients.create(builder, props.identity());
    }

    /** Reference image for gallery building. The caller embeds it and discards it; it is never stored. */
    public byte[] referenceImage(String profileId) {
        try {
            return http.get().uri("/v1/profiles/{id}/reference-image", profileId).retrieve().body(byte[].class);
        } catch (RestClientException e) {
            throw new DependencyUnavailableException(ReasonCode.GALLERY_UNAVAILABLE, e);
        }
    }

    /**
     * 1:1 confirmation before boarding, behind a circuit breaker (see ModelClient.embed for how the proxy works).
     * No retry: on the gate's hot path a fast MANUAL beats a slow retry, and retries add load to a struggling IdP.
     */
    @CircuitBreaker(name = "identity", fallbackMethod = "unavailable")
    public boolean isValid(String profileId, String flightId) {
        VerifyResponse response = http.post()
                .uri("/v1/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new VerifyRequest(profileId, flightId))
                .retrieve()
                .body(VerifyResponse.class);
        return response != null && "VALID".equals(response.result());
    }

    private boolean unavailable(String profileId, String flightId, Throwable cause) {
        throw new DependencyUnavailableException(ReasonCode.IDP_UNAVAILABLE, cause);
    }
}
