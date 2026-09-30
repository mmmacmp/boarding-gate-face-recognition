package poc.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import poc.gate.crypto.TemplateCrypto;
import poc.gate.decision.ReasonCode;
import poc.gate.gallery.AuditLog;
import poc.gate.gallery.AuditLogRepository;
import poc.gate.gallery.Flight;
import poc.gate.gallery.GalleryEntry;
import poc.gate.gallery.PurgeJob;

/** Retention: after departure (+ grace, 0 s in tests) the PurgeJob crypto-shreds the flight. */
class RetentionIT extends AbstractGateIT {

    @Autowired
    private PurgeJob purgeJob;
    @Autowired
    private TemplateCrypto crypto;
    @Autowired
    private AuditLogRepository audit;

    @Test
    void purgeCryptoShredsTheFlightSoEvenACopiedCiphertextCannotBeDecrypted() {
        String flightId = boardingFlight(passengers(unique("A"), unit(1, 0, 0, 0)));
        GalleryEntry row = entries.findMatchable(flightId, MODEL).get(0);
        TemplateCrypto.Encrypted backupCopy = row.encrypted(); // e.g. the row as it sits in yesterday's backup
        String aad = row.aad();
        byte[] wrappedBefore = flights.findById(flightId).orElseThrow().getWrappedDek();
        assertThat(crypto.decrypt(crypto.unwrap(wrappedBefore), backupCopy, aad)).hasSize(4); // readable before

        gallery.depart(flightId);
        purgeJob.run();

        Flight after = flights.findById(flightId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(Flight.Status.PURGED);
        assertThat(after.getWrappedDek()).isNull();
        assertThat(entries.countByStatus(flightId, "ACTIVE") + entries.countByStatus(flightId, "BOARDED")).isZero();
        assertThatThrownBy(() -> crypto.decrypt(crypto.unwrap(after.getWrappedDek()), backupCopy, aad))
                .isInstanceOf(IllegalStateException.class);
        assertThat(audit.findByFlightId(flightId)).extracting(AuditLog::getAction).containsExactly("PURGE");
        assertThat(event("G1", flightId, probe(unit(1, 0, 0, 0))).getReason())
                .isEqualTo(ReasonCode.GALLERY_UNAVAILABLE);
    }

    @Test
    void departedFlightCannotBeRebuilt() {
        String flightId = boardingFlight(passengers(unique("A"), unit(1, 0, 0, 0)));
        gallery.depart(flightId);
        assertThatThrownBy(() -> gallery.build(flightId)).isInstanceOf(IllegalStateException.class);
    }
}
