package poc.gate.decision;

/** One gallery entry and its cosine similarity to the probe. */
public record Candidate(long entryId, String profileId, double score) {
}
