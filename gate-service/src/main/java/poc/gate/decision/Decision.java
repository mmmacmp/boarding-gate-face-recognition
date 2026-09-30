package poc.gate.decision;

/**
 * Immutable gate decision. entryId and profileId are only set for BOARD, so a MANUAL decision can never
 * leak who the passenger probably was. Scores (nullable) are kept for metrics.
 */
public record Decision(Outcome outcome, ReasonCode reason, Long entryId, String profileId, Double top1, Double top2) {

    public enum Outcome { BOARD, MANUAL }

    public static Decision manual(ReasonCode reason) {
        return new Decision(Outcome.MANUAL, reason, null, null, null, null);
    }

    public static Decision manual(ReasonCode reason, Double top1, Double top2) {
        return new Decision(Outcome.MANUAL, reason, null, null, top1, top2);
    }

    public static Decision board(Candidate best, Double top2) {
        return new Decision(Outcome.BOARD, ReasonCode.MATCHED, best.entryId(), best.profileId(), best.score(), top2);
    }

    /** Downgrade a BOARD candidate (e.g. identity provider said INVALID): drop the identity, keep the scores. */
    public Decision toManual(ReasonCode newReason) {
        return manual(newReason, top1, top2);
    }

    public boolean isBoard() {
        return outcome == Outcome.BOARD;
    }
}
