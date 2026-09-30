package poc.gate.gallery;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import poc.gate.clients.DcsClient;
import poc.gate.clients.IdentityClient;
import poc.gate.clients.ModelClient;
import poc.gate.crypto.TemplateCrypto;
import poc.gate.decision.Candidate;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Builds a flight's encrypted gallery and searches it. */
@Service
public class GalleryService {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record BuildResult(String flightId, Flight.Status status, int enrolled, int notConsented, int rejected,
                              int alreadyBoarded) {
    }

    private final FlightRepository flights;
    private final PassengerConsentRepository consents;
    private final GalleryEntryRepository entries;
    private final DcsClient dcs;
    private final IdentityClient identity;
    private final ModelClient model;
    private final TemplateCrypto crypto;
    private final AuditLogRepository audit;

    // Constructor injection: Spring passes in the beans (like FastAPI Depends), and the fields can be final.
    public GalleryService(FlightRepository flights, PassengerConsentRepository consents, GalleryEntryRepository entries,
                          DcsClient dcs, IdentityClient identity, ModelClient model, TemplateCrypto crypto,
                          AuditLogRepository audit) {
        this.flights = flights;
        this.consents = consents;
        this.entries = entries;
        this.dcs = dcs;
        this.identity = identity;
        this.model = model;
        this.crypto = crypto;
        this.audit = audit;
    }

    /**
     * Manifest -> consented passengers only -> reference image -> embed -> encrypt -> store.
     * Deliberately NOT @Transactional: it makes many slow HTTP calls, and a DB transaction must not stay open
     * across them. If a dependency fails half-way, the exception propagates and the flight stays BUILDING,
     * so every gate event for it gets MANUAL / GALLERY_UNAVAILABLE.
     * A rebuild preserves boarded state: only ACTIVE rows are replaced, and BOARDED passengers are not re-enrolled.
     */
    public BuildResult build(String flightId) {
        Flight flight = flights.findById(flightId).orElseGet(() -> new Flight(flightId));
        if (flight.isClosed()) {
            throw new IllegalStateException("flight " + flightId + " has departed; its gallery cannot be rebuilt");
        }
        DcsClient.Manifest manifest = dcs.manifest(flightId);
        entries.deleteActiveByFlight(flightId);
        Set<String> boarded = entries.findBoardedProfileIds(flightId);
        flight.startBuild(manifest.departureTime(), crypto.wrap(crypto.newDek()));
        flights.save(flight);
        SecretKey dek = crypto.unwrap(flight.getWrappedDek());

        int enrolled = 0;
        int notConsented = 0;
        int rejected = 0;
        int alreadyBoarded = 0;
        String modelId = null;
        for (DcsClient.Passenger passenger : manifest.passengers()) {
            String profileId = passenger.profileId();
            if (boarded.contains(profileId)) {
                alreadyBoarded++;
                continue; // boarded once is boarded: a rebuild must never let them board again
            }
            if (!consents.findById(profileId).map(PassengerConsent::isActive).orElse(false)) {
                notConsented++;
                continue; // no consent: we never even fetch the reference image
            }
            ModelClient.EmbedResponse reference = model.embed(identity.referenceImage(profileId)); // image not kept
            modelId = reference.modelId();
            if (reference.faceCount() != 1) {
                rejected++;
                continue;
            }
            String aad = TemplateCrypto.aad(flightId, profileId, modelId);
            entries.save(new GalleryEntry(flightId, profileId, modelId, crypto.encrypt(dek, reference.embedding(), aad)));
            enrolled++;
        }
        flight.openBoarding(modelId);
        flights.save(flight);
        return new BuildResult(flightId, flight.getStatus(), enrolled, notConsented, rejected, alreadyBoarded);
    }

    /** Close boarding. Templates are kept until the PurgeJob runs after the grace period. */
    public Flight depart(String flightId) {
        Flight flight = flights.findById(flightId).orElseThrow();
        flight.depart(Instant.now());
        return flights.save(flight);
    }

    /**
     * Retention: delete the flight's gallery rows AND its wrapped DEK, in one transaction.
     * Called from PurgeJob, a different bean, so the call goes through this bean's transactional proxy.
     */
    @Transactional
    public int purge(String flightId) {
        Flight flight = flights.findById(flightId).orElseThrow();
        int deleted = entries.deleteByFlight(flightId);
        flight.shred(); // managed entity: Hibernate writes wrapped_dek = NULL at commit (dirty checking)
        audit.save(new AuditLog("PURGE", flightId, "deleted " + deleted + " gallery entries; wrapped DEK destroyed"));
        return deleted;
    }

    /**
     * Exact 1:N search over the matchable gallery; returns the best two candidates.
     * Templates are decrypted per request and not cached, so plaintext only lives for the duration of one event.
     */
    public List<Candidate> searchTop2(Flight flight, String modelId, float[] probe) {
        SecretKey dek = crypto.unwrap(flight.getWrappedDek());
        List<Candidate> scored = new ArrayList<>();
        for (GalleryEntry entry : entries.findMatchable(flight.getFlightId(), modelId)) {
            float[] template = crypto.decrypt(dek, entry.encrypted(), entry.aad());
            scored.add(new Candidate(entry.getId(), entry.getProfileId(), dot(probe, template)));
        }
        return scored.stream().sorted(Comparator.comparingDouble(Candidate::score).reversed()).limit(2).toList();
    }

    /** Both vectors are L2-normalised by the model-service, so the dot product is the cosine similarity. */
    static double dot(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }
}
