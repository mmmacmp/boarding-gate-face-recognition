package poc.gate.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import poc.gate.clients.DependencyUnavailableException;
import poc.gate.clients.IdentityClient;
import poc.gate.clients.ModelClient;
import poc.gate.decision.Candidate;
import poc.gate.decision.Decision;
import poc.gate.decision.DecisionPolicy;
import poc.gate.decision.ReasonCode;
import poc.gate.gallery.Flight;
import poc.gate.gallery.FlightRepository;
import poc.gate.gallery.GalleryService;
import poc.gate.metrics.GateMetrics;

/** Unit test with Mockito fakes (like unittest.mock): no database, no HTTP. The real DecisionPolicy is used. */
class GateDecisionServiceTest {

    private static final String MODEL = "r50-buffalo_l";
    private static final byte[] IMAGE = {1, 2, 3};
    private static final float[] PROBE = {1f, 0f};

    private final FlightRepository flights = mock(FlightRepository.class);
    private final ModelClient model = mock(ModelClient.class);
    private final GalleryService gallery = mock(GalleryService.class);
    private final IdentityClient identity = mock(IdentityClient.class);
    private final BoardingWriter writer = mock(BoardingWriter.class);
    private final GateEventRepository events = mock(GateEventRepository.class); // findBy... -> Optional.empty()
    private final GateDecisionService service = new GateDecisionService(flights, model, gallery,
            new DecisionPolicy(Map.of(MODEL, 0.35), 0.10), identity, writer, events,
            new GateMetrics(new SimpleMeterRegistry()));

    @BeforeEach
    void setUp() {
        Flight flight = new Flight("POC001");
        flight.openBoarding(MODEL);
        when(flights.findById("POC001")).thenReturn(Optional.of(flight));
        when(model.embed(IMAGE)).thenReturn(oneFace());
        when(gallery.search(any(), anyString(), any())).thenReturn(
                List.of(new Candidate(7, "P001", 0.60), new Candidate(8, "P002", 0.20)));
        when(identity.isValid("P001", "POC001")).thenReturn(true);
        // The writer just turns the decision into an event (the conditional update is tested in Step 4).
        when(writer.record(anyString(), anyString(), anyString(), any())).thenAnswer(inv ->
                new GateEvent(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)));
    }

    private static ModelClient.EmbedResponse oneFace() {
        return new ModelClient.EmbedResponse(MODEL, 2, List.of(new ModelClient.Face(List.of(0d, 0d, 9d, 9d), 0.9, PROBE)), 0);
    }

    private GateEvent handle() {
        return service.handle("G1", "POC001", "key-1", IMAGE);
    }

    @Test
    void happyPathBoardsTheBestCandidate() {
        GateEvent event = handle();
        assertThat(event.getOutcome()).isEqualTo(Decision.Outcome.BOARD);
        assertThat(event.getReason()).isEqualTo(ReasonCode.MATCHED);
        assertThat(event.getProfileId()).isEqualTo("P001");
        assertThat(event.getTop1()).isEqualTo(0.60);
    }

    @Test
    void happyPathReportsTheTimeOfEveryStage() {
        StageTimings t = handle().getTimings();
        assertThat(List.of(t.embedMs, t.searchMs, t.identityMs, t.dbMs)).doesNotContainNull();
    }

    @Test
    void onlyFacesTooSmallIsLowQualityWithoutSearching() {
        when(model.embed(IMAGE)).thenReturn(new ModelClient.EmbedResponse(MODEL, 2, List.of(), 1));
        GateEvent event = handle();
        assertThat(event.getReason()).isEqualTo(ReasonCode.LOW_QUALITY);
        assertThat(event.getTimings().searchMs).isNull(); // the search never ran
        verify(gallery, never()).search(any(), anyString(), any());
    }

    @Test
    void identityProviderRejectionIsManualWithoutIdentity() {
        when(identity.isValid("P001", "POC001")).thenReturn(false);
        GateEvent event = handle();
        assertThat(event.getReason()).isEqualTo(ReasonCode.IDP_REJECTED);
        assertThat(event.getProfileId()).isNull();
    }

    @Test
    void modelServiceFailureIsManual() {
        when(model.embed(IMAGE)).thenThrow(new DependencyUnavailableException(ReasonCode.MODEL_UNAVAILABLE,
                new RuntimeException("timeout")));
        assertThat(handle().getReason()).isEqualTo(ReasonCode.MODEL_UNAVAILABLE);
    }

    @Test
    void identityProviderFailureIsManual() {
        when(identity.isValid("P001", "POC001")).thenThrow(new DependencyUnavailableException(
                ReasonCode.IDP_UNAVAILABLE, new RuntimeException("503")));
        assertThat(handle().getReason()).isEqualTo(ReasonCode.IDP_UNAVAILABLE);
    }

    @Test
    void flightNotBoardingIsGalleryUnavailableAndSkipsTheModel() {
        when(flights.findById("POC001")).thenReturn(Optional.of(new Flight("POC001"))); // no status yet
        assertThat(handle().getReason()).isEqualTo(ReasonCode.GALLERY_UNAVAILABLE);
        verify(model, never()).embed(any());
    }

    @Test
    void probeFromADifferentModelIsNeverCompared() {
        when(model.embed(IMAGE)).thenReturn(new ModelClient.EmbedResponse("sface-2021dec", 128, oneFace().faces(), 0));
        assertThat(handle().getReason()).isEqualTo(ReasonCode.MODEL_MISMATCH);
        verify(gallery, never()).search(any(), anyString(), any());
    }

    @Test
    void unexpectedErrorIsStillManual() {
        when(gallery.search(any(), anyString(), any())).thenThrow(new IllegalStateException("cannot decrypt"));
        assertThat(handle().getReason()).isEqualTo(ReasonCode.INTERNAL_ERROR);
    }

    @Test
    void twoFacesIsMultiFaceWithoutSearching() {
        ModelClient.Face face = oneFace().faces().get(0);
        when(model.embed(IMAGE)).thenReturn(new ModelClient.EmbedResponse(MODEL, 2, List.of(face, face), 0));
        assertThat(handle().getReason()).isEqualTo(ReasonCode.MULTI_FACE);
        verify(gallery, never()).search(any(), anyString(), any());
    }
}
