package poc.gate.gallery;

import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface GalleryEntryRepository extends JpaRepository<GalleryEntry, Long> {

    /**
     * The matchable gallery: this flight, this model, not yet boarded, and consent ACTIVE right now.
     * The consent join runs on every gate event, so a withdrawal takes effect on the very next match.
     */
    @Query(value = """
            SELECT e.* FROM gallery_entry e
            JOIN passenger_consent c ON c.profile_id = e.profile_id AND c.status = 'ACTIVE'
            WHERE e.flight_id = :flightId AND e.model_id = :modelId AND e.status = 'ACTIVE'
            """, nativeQuery = true)
    List<GalleryEntry> findMatchable(String flightId, String modelId);

    /**
     * Board at most once. The WHERE status = 'ACTIVE' makes it a compare-and-set in the database:
     * if two gates race, the row lock lets one update win (1 row) and the other sees 0 rows.
     * Needs a surrounding transaction (BoardingWriter).
     */
    @Modifying
    @Query(value = "UPDATE gallery_entry SET status = 'BOARDED' WHERE id = :id AND status = 'ACTIVE'",
            nativeQuery = true)
    int markBoarded(long id);

    @Query(value = "SELECT profile_id FROM gallery_entry WHERE flight_id = :flightId AND status = 'BOARDED'",
            nativeQuery = true)
    Set<String> findBoardedProfileIds(String flightId);

    @Query(value = "SELECT profile_id FROM gallery_entry WHERE flight_id = :flightId AND status = 'ACTIVE'",
            nativeQuery = true)
    Set<String> findActiveProfileIds(String flightId);

    /** Rebuild replaces only ACTIVE rows: BOARDED rows are the record that a passenger already boarded. */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM gallery_entry WHERE flight_id = :flightId AND status = 'ACTIVE'", nativeQuery = true)
    int deleteActiveByFlight(String flightId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM gallery_entry WHERE profile_id = :profileId AND status = 'ACTIVE'",
            nativeQuery = true)
    int deleteActiveByProfile(String profileId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM gallery_entry WHERE flight_id = :flightId", nativeQuery = true)
    int deleteByFlight(String flightId);

    @Query(value = "SELECT count(*) FROM gallery_entry WHERE flight_id = :flightId AND status = :status",
            nativeQuery = true)
    long countByStatus(String flightId, String status);
}
