package poc.gate.gallery;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import poc.gate.crypto.TemplateCrypto;

/** One passenger's encrypted template for one flight and one model. The plaintext is never stored. */
@Entity
public class GalleryEntry {

    public enum Status { ACTIVE, BOARDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // the database assigns the id (bigserial)
    private Long id;
    private String flightId;
    private String profileId;
    private String modelId;
    private byte[] iv;
    private byte[] ciphertext;
    @Enumerated(EnumType.STRING)
    private Status status;
    private Instant createdAt;

    protected GalleryEntry() {
    }

    public GalleryEntry(String flightId, String profileId, String modelId, TemplateCrypto.Encrypted template) {
        this.flightId = flightId;
        this.profileId = profileId;
        this.modelId = modelId;
        this.iv = template.iv();
        this.ciphertext = template.ciphertext();
        this.status = Status.ACTIVE;
        this.createdAt = Instant.now();
    }

    public TemplateCrypto.Encrypted encrypted() {
        return new TemplateCrypto.Encrypted(iv, ciphertext);
    }

    /** Must match the AAD used at encryption time, or decryption fails. */
    public String aad() {
        return TemplateCrypto.aad(flightId, profileId, modelId);
    }

    public Long getId() {
        return id;
    }

    public String getProfileId() {
        return profileId;
    }
}
