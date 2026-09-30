package poc.gate.gallery;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/** Append-only record of retention actions (consent withdrawal, purge / crypto-shred). No biometrics. */
@Entity
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String action;
    private String flightId;
    private String detail;
    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(String action, String flightId, String detail) {
        this.action = action;
        this.flightId = flightId;
        this.detail = detail;
        this.createdAt = Instant.now();
    }

    public String getAction() {
        return action;
    }

    public String getDetail() {
        return detail;
    }
}
