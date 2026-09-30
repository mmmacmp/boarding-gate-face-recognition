package poc.gate.events;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import poc.gate.decision.Decision;
import poc.gate.decision.ReasonCode;
import poc.gate.gallery.GalleryEntryRepository;

/**
 * The only database write of a gate event, in one transaction.
 *
 * Why a separate bean: @Transactional works through a Spring proxy that wraps this object. The proxy opens the
 * transaction when ANOTHER bean calls record(); a call from inside the same class would skip the proxy and run
 * without a transaction. Keeping it here also guarantees no HTTP call happens while the transaction is open.
 */
@Component
public class BoardingWriter {

    private final GalleryEntryRepository entries;
    private final GateEventRepository events;

    public BoardingWriter(GalleryEntryRepository entries, GateEventRepository events) {
        this.entries = entries;
        this.events = events;
    }

    @Transactional // commit if the method returns normally, roll back if it throws
    public GateEvent record(String idempotencyKey, String gateId, String flightId, Decision decision) {
        Decision finalDecision = decision;
        if (decision.isBoard() && entries.markBoarded(decision.entryId()) == 0) {
            finalDecision = decision.toManual(ReasonCode.ALREADY_BOARDED); // another gate boarded them first
        }
        return events.save(new GateEvent(idempotencyKey, gateId, flightId, finalDecision));
    }
}
