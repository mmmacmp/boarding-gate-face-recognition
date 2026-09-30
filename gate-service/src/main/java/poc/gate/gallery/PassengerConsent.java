package poc.gate.gallery;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;

/** A passenger's biometric-boarding consent. Checked when the gallery is built AND on every match. */
@Entity
public class PassengerConsent {

    public enum Status { ACTIVE, WITHDRAWN }

    @Id
    private String profileId;
    @Enumerated(EnumType.STRING)
    private Status status;
    private Instant updatedAt;

    protected PassengerConsent() {
    }

    public PassengerConsent(String profileId) {
        this.profileId = profileId;
        this.status = Status.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public String getProfileId() {
        return profileId;
    }

    public Status getStatus() {
        return status;
    }
}
