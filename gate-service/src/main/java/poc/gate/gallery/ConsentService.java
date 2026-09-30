package poc.gate.gallery;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Opt-in and opt-out. Withdrawal removes the passenger's matchable templates in the same transaction. */
@Service
public class ConsentService {

    public record Withdrawal(String profileId, PassengerConsent.Status status, int removedEntries) {
    }

    private final PassengerConsentRepository consents;
    private final GalleryEntryRepository entries;
    private final AuditLogRepository audit;

    public ConsentService(PassengerConsentRepository consents, GalleryEntryRepository entries,
                          AuditLogRepository audit) {
        this.consents = consents;
        this.entries = entries;
        this.audit = audit;
    }

    @Transactional
    public PassengerConsent grant(String profileId) {
        return consents.save(new PassengerConsent(profileId)); // save = insert, or update if the id exists
    }

    /**
     * Inside @Transactional, the loaded consent is a "managed" entity: changing it (withdraw()) is enough, Hibernate
     * writes the UPDATE at commit (dirty checking), no save() needed. BOARDED rows stay as the record of boarding.
     */
    @Transactional
    public Optional<Withdrawal> withdraw(String profileId) {
        return consents.findById(profileId).map(consent -> {
            consent.withdraw();
            int removed = entries.deleteActiveByProfile(profileId);
            audit.save(new AuditLog("CONSENT_WITHDRAWN", null, "removed " + removed + " gallery entries"));
            return new Withdrawal(profileId, consent.getStatus(), removed);
        });
    }
}
