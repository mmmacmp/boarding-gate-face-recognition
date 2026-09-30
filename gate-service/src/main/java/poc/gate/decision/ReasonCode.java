package poc.gate.decision;

/** Why the gate decided what it did. Every MANUAL decision carries one; BOARD carries MATCHED. */
public enum ReasonCode {
    MATCHED,
    NO_FACE,
    MULTI_FACE,
    NO_MATCH,
    AMBIGUOUS,
    GALLERY_UNAVAILABLE,
    MODEL_UNAVAILABLE,
    MODEL_MISMATCH,
    IDP_UNAVAILABLE,
    IDP_REJECTED,
    ALREADY_BOARDED,
    INTERNAL_ERROR
}
