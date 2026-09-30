package poc.gate.gallery;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import poc.gate.config.GateProperties;

/** Background retention job (like an APScheduler interval job): crypto-shreds departed flights after a grace period. */
@Component
public class PurgeJob {

    private static final Logger log = LoggerFactory.getLogger(PurgeJob.class);

    private final FlightRepository flights;
    private final GalleryService gallery;
    private final GateProperties props;

    public PurgeJob(FlightRepository flights, GalleryService gallery, GateProperties props) {
        this.flights = flights;
        this.gallery = gallery;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${gate.retention.purge-interval}")
    public void run() {
        Instant cutoff = Instant.now().minus(props.retention().grace());
        for (Flight flight : flights.findByStatusAndDepartedAtBefore(Flight.Status.DEPARTED, cutoff)) {
            int deleted = gallery.purge(flight.getFlightId()); // one transaction per flight
            log.info("purge flight={} deleted_entries={} dek=destroyed", flight.getFlightId(), deleted);
        }
    }
}
