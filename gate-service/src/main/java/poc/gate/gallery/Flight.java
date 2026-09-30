package poc.gate.gallery;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * JPA entity = one row of the flight table (like a SQLAlchemy model). Field names map to snake_case columns
 * automatically (departureTime -> departure_time). Holds the flight's wrapped data key.
 */
@Entity
public class Flight {

    public enum Status { BUILDING, BOARDING, DEPARTED, PURGED }

    @Id
    private String flightId;
    @Enumerated(EnumType.STRING)
    private Status status;
    private Instant departureTime;
    private String modelId;
    private byte[] wrappedDek;
    private Instant departedAt;

    protected Flight() {
        // required by JPA, which creates the object first and then fills the fields from the row
    }

    public Flight(String flightId) {
        this.flightId = flightId;
    }

    /** (Re)build keeps an existing key, so rows that stay (BOARDED) remain consistent with the flight's DEK. */
    public void startBuild(Instant departureTime, byte[] newWrappedDekIfMissing) {
        this.status = Status.BUILDING;
        this.departureTime = departureTime;
        this.modelId = null;
        if (wrappedDek == null) {
            wrappedDek = newWrappedDekIfMissing;
        }
    }

    public void openBoarding(String modelId) {
        this.status = Status.BOARDING;
        this.modelId = modelId;
    }

    public void depart(Instant now) {
        this.status = Status.DEPARTED;
        this.departedAt = now;
    }

    /** Crypto-shred: without the wrapped DEK, every template of this flight is unreadable, including backups. */
    public void shred() {
        this.wrappedDek = null;
        this.status = Status.PURGED;
    }

    public boolean isClosed() {
        return status == Status.DEPARTED || status == Status.PURGED;
    }

    public String getFlightId() {
        return flightId;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getDepartureTime() {
        return departureTime;
    }

    public String getModelId() {
        return modelId;
    }

    public byte[] getWrappedDek() {
        return wrappedDek;
    }
}
