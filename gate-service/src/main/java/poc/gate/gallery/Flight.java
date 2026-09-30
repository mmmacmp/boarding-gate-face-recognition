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

    protected Flight() {
        // required by JPA, which creates the object first and then fills the fields from the row
    }

    public Flight(String flightId) {
        this.flightId = flightId;
    }

    /** A (re)build gets a fresh data key; templates encrypted under an old key become unreadable. */
    public void startBuild(Instant departureTime, byte[] wrappedDek) {
        this.status = Status.BUILDING;
        this.departureTime = departureTime;
        this.wrappedDek = wrappedDek;
        this.modelId = null;
    }

    public void openBoarding(String modelId) {
        this.status = Status.BOARDING;
        this.modelId = modelId;
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
