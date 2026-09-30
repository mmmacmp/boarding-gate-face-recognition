package poc.gate;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import poc.gate.decision.Decision;
import poc.gate.decision.ReasonCode;
import poc.gate.events.GateEvent;
import poc.gate.gallery.GalleryService;

/** Failure-mode table rows about boarding, consent and privacy, against real Postgres. */
@ExtendWith(OutputCaptureExtension.class)
class BoardingIT extends AbstractGateIT {

    private static final float[] FACE_A = unit(1, 0, 0, 0);
    private static final float[] FACE_B = unit(0, 1, 0, 0);

    @Autowired
    private MeterRegistry meters;

    @Test
    void boardsOnceAndBoardedPassengerLeavesTheGallery() {
        String a = unique("A");
        String flight = boardingFlight(passengers(a, FACE_A, unique("B"), FACE_B));
        byte[] capture = probe(unit(0.95, 0.05, 0, 0));

        GateEvent first = event("G1", flight, capture);
        assertThat(first.getOutcome()).isEqualTo(Decision.Outcome.BOARD);
        assertThat(first.getProfileId()).isEqualTo(a);
        assertThat(entries.countByStatus(flight, "BOARDED")).isEqualTo(1);
        assertThat(entries.countByStatus(flight, "ACTIVE")).isEqualTo(1);

        assertThat(event("G1", flight, capture).getReason()).isEqualTo(ReasonCode.NO_MATCH); // no second boarding
    }

    @Test
    void duplicateIdempotencyKeyReturnsTheStoredDecisionWithoutReprocessing() {
        String flight = boardingFlight(passengers(unique("A"), FACE_A));
        byte[] capture = probe(FACE_A);

        GateEvent first = gate.handle("G1", flight, "retry-" + flight, capture);
        GateEvent retry = gate.handle("G1", flight, "retry-" + flight, capture);

        assertThat(retry.getId()).isEqualTo(first.getId());
        assertThat(retry.getOutcome()).isEqualTo(Decision.Outcome.BOARD); // not ALREADY_BOARDED or NO_MATCH
        wiremock.verify(1, postRequestedFor(urlEqualTo("/v1/verify")));
    }

    @Test
    void twoGatesSamePassengerAtTheSameTimeBoardExactlyOnce() throws Exception {
        String flight = boardingFlight(passengers(unique("A"), FACE_A));
        stubVerify(verifyResult("VALID").withFixedDelay(150)); // keeps both requests in flight together
        byte[] capture = probe(FACE_A);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<GateEvent>> results = List.of("G1", "G2").stream()
                .map(g -> pool.submit(() -> {
                    start.await();
                    return event(g, flight, capture);
                })).toList();
        start.countDown();
        List<ReasonCode> reasons = results.stream().map(f -> {
            try {
                return f.get().getReason();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).toList();
        pool.shutdown();

        assertThat(reasons).containsExactlyInAnyOrder(ReasonCode.MATCHED, ReasonCode.ALREADY_BOARDED);
        assertThat(entries.countByStatus(flight, "BOARDED")).isEqualTo(1);
    }

    @Test
    void consentWithdrawnMidBoardingMeansNoMatchOnTheNextAttempt() {
        String a = unique("A");
        String flight = boardingFlight(passengers(a, FACE_A, unique("B"), FACE_B));

        assertThat(consents.withdraw(a)).get().extracting(w -> w.removedEntries()).isEqualTo(1);
        GateEvent attempt = event("G1", flight, probe(FACE_A));

        assertThat(attempt.getReason()).isEqualTo(ReasonCode.NO_MATCH);
        assertThat(attempt.getProfileId()).isNull();
    }

    @Test
    void lookAlikesInTheSameFlightGoToManualCheck() {
        String flight = boardingFlight(passengers(unique("T"), unit(1, 0.05, 0, 0), unique("T"), unit(1, 0.07, 0, 0)));
        GateEvent event = event("G1", flight, probe(unit(1, 0.06, 0, 0)));
        assertThat(event.getReason()).isEqualTo(ReasonCode.AMBIGUOUS);
        assertThat(event.getProfileId()).isNull();
    }

    @Test
    void strangerIsNoMatchAndNoIdentityIsLogged(CapturedOutput output) {
        String a = unique("A");
        String b = unique("B");
        String flight = boardingFlight(passengers(a, FACE_A, b, FACE_B));

        GateEvent event = event("G9", flight, probe(unit(0, 0, 1, 0)));

        assertThat(event.getReason()).isEqualTo(ReasonCode.NO_MATCH);
        assertThat(output).contains("gate=G9 flight=" + flight + " decision=MANUAL reason=NO_MATCH");
        assertThat(output).contains("profile=-").doesNotContain(a).doesNotContain(b);
    }

    @Test
    void multipleFacesGoToManualCheck() {
        String flight = boardingFlight(passengers(unique("A"), FACE_A));
        assertThat(event("G1", flight, probe(FACE_A, FACE_B)).getReason()).isEqualTo(ReasonCode.MULTI_FACE);
    }

    @Test
    void identityProviderInvalidIsRejectedAndPassengerStaysInGallery() {
        String flight = boardingFlight(passengers(unique("A"), FACE_A));
        stubVerify(verifyResult("INVALID"));
        assertThat(event("G1", flight, probe(FACE_A)).getReason()).isEqualTo(ReasonCode.IDP_REJECTED);
        assertThat(entries.countByStatus(flight, "ACTIVE")).isEqualTo(1);
    }

    @Test
    void rebuildPreservesBoardedStateSoNobodyBoardsTwice() {
        String a = unique("A");
        String b = unique("B");
        String flight = boardingFlight(passengers(a, FACE_A, b, FACE_B));
        byte[] capture = probe(FACE_A);
        assertThat(event("G1", flight, capture).getOutcome()).isEqualTo(Decision.Outcome.BOARD);

        GalleryService.BuildResult rebuild = gallery.build(flight);

        assertThat(rebuild.alreadyBoarded()).isEqualTo(1);
        assertThat(rebuild.enrolled()).isEqualTo(1); // only b is matchable again
        GateEvent again = event("G2", flight, capture);
        assertThat(again.getOutcome()).isEqualTo(Decision.Outcome.MANUAL);
        assertThat(again.getReason()).isEqualTo(ReasonCode.NO_MATCH);
        assertThat(entries.countByStatus(flight, "BOARDED")).isEqualTo(1);
    }

    @Test
    void nonConsentedManifestPassengerIsNeverEnrolledNorFetched() {
        String consented = unique("A");
        String notConsented = unique("N");
        String flight = unique("IT");
        consents.grant(consented);
        stubPassenger(consented, FACE_A);
        stubManifest(flight, List.of(consented, notConsented));

        GalleryService.BuildResult result = gallery.build(flight);

        assertThat(result.enrolled()).isEqualTo(1);
        assertThat(result.notConsented()).isEqualTo(1);
        wiremock.verify(0, getRequestedFor(urlEqualTo("/v1/profiles/" + notConsented + "/reference-image")));
    }

    @Test
    void decisionsAreCountedInMetrics() {
        String flight = boardingFlight(passengers(unique("A"), FACE_A));
        event("GM", flight, probe(FACE_A));
        assertThat(meters.get("gate.decisions").tags("gate", "GM", "decision", "BOARD", "reason", "MATCHED")
                .counter().count()).isEqualTo(1.0);
        assertThat(meters.get("gate.decision").timer().count()).isPositive();
    }
}
