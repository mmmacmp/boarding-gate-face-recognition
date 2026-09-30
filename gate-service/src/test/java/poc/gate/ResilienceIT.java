package poc.gate;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.Fault;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.List;
import org.junit.jupiter.api.Test;
import poc.gate.clients.DependencyUnavailableException;
import poc.gate.decision.ReasonCode;
import poc.gate.gallery.Flight;

/** Failure-mode table rows about slow or broken dependencies: every one must end in a fast MANUAL decision. */
class ResilienceIT extends AbstractGateIT {

    private static final float[] FACE_A = unit(1, 0, 0, 0);

    private String flight;

    private String flightWithOnePassenger() {
        flight = boardingFlight(passengers(unique("A"), FACE_A));
        return flight;
    }

    private ReasonCode attempt() {
        return event("G1", flight, probe(FACE_A)).getReason();
    }

    @Test
    void slowIdentityProviderIsIdpUnavailableWithinTheTimeout() {
        flightWithOnePassenger();
        stubVerify(verifyResult("VALID").withFixedDelay(2000)); // the demo's "2 s latency" fault
        long start = System.nanoTime();
        assertThat(attempt()).isEqualTo(ReasonCode.IDP_UNAVAILABLE);
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(1000); // 300 ms read timeout, not 2 s
    }

    @Test
    void identityProviderServerErrorIsIdpUnavailable() {
        flightWithOnePassenger();
        stubVerify(serverError());
        assertThat(attempt()).isEqualTo(ReasonCode.IDP_UNAVAILABLE);
    }

    @Test
    void identityProviderConnectionResetIsIdpUnavailable() {
        flightWithOnePassenger();
        stubVerify(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));
        assertThat(attempt()).isEqualTo(ReasonCode.IDP_UNAVAILABLE);
    }

    @Test
    void breakerOpensThenFailsFastThenRecoversThroughHalfOpen() throws InterruptedException {
        flightWithOnePassenger();
        CircuitBreaker breaker = breakers.circuitBreaker("identity");
        stubVerify(serverError());
        for (int i = 0; i < 5; i++) {                          // minimum-number-of-calls = 5, all failing
            assertThat(attempt()).isEqualTo(ReasonCode.IDP_UNAVAILABLE);
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        stubVerify(verifyResult("INVALID"));                  // IdP healthy again (INVALID = a successful call)
        assertThat(attempt()).isEqualTo(ReasonCode.IDP_UNAVAILABLE); // still OPEN: rejected without a request
        wiremock.verify(5, postRequestedFor(urlEqualTo("/v1/verify")));

        Thread.sleep(1100);                                    // wait-duration-in-open-state = 1 s in tests
        assertThat(attempt()).isEqualTo(ReasonCode.IDP_REJECTED); // trial call 1 (HALF_OPEN)
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        assertThat(attempt()).isEqualTo(ReasonCode.IDP_REJECTED); // trial call 2 succeeds -> CLOSED
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void modelServiceDownIsModelUnavailable() {
        flightWithOnePassenger();
        byte[] capture = "down-capture".getBytes();
        stubEmbed(capture, aResponse().withStatus(503));
        assertThat(event("G1", flight, capture).getReason()).isEqualTo(ReasonCode.MODEL_UNAVAILABLE);
    }

    @Test
    void slowModelServiceIsModelUnavailable() {
        flightWithOnePassenger();
        byte[] capture = "slow-capture".getBytes();
        stubEmbed(capture, aResponse().withStatus(200).withFixedDelay(1500)); // > 800 ms read timeout
        assertThat(event("G1", flight, capture).getReason()).isEqualTo(ReasonCode.MODEL_UNAVAILABLE);
    }

    @Test
    void identityProviderDownDuringGalleryBuildMeansTheWholeFlightBoardsManually() {
        String profile = unique("A");
        String flightId = unique("IT");
        consents.grant(profile);
        stubManifest(flightId, List.of(profile));
        wiremock.stubFor(get("/v1/profiles/" + profile + "/reference-image").willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> gallery.build(flightId))
                .isInstanceOfSatisfying(DependencyUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(ReasonCode.GALLERY_UNAVAILABLE));
        assertThat(flights.findById(flightId)).get().extracting(Flight::getStatus).isEqualTo(Flight.Status.BUILDING);
        assertThat(event("G1", flightId, probe(FACE_A)).getReason()).isEqualTo(ReasonCode.GALLERY_UNAVAILABLE);
    }
}
