package poc.gate.decision;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The gate's matching rules as a pure function: no Spring, no I/O, so it is tested with plain JUnit.
 * Thresholds are per model (scores from different models are not comparable) and are ILLUSTRATIVE placeholders.
 */
public class DecisionPolicy {

    private final Map<String, Double> thresholds; // model_id -> T
    private final double margin;                  // Δ: minimum gap between the best and second-best score

    public DecisionPolicy(Map<String, Double> thresholds, double margin) {
        this.thresholds = Map.copyOf(thresholds);
        this.margin = margin;
    }

    public Decision decide(int faceCount, String modelId, List<Candidate> candidates) {
        if (faceCount == 0) {
            return Decision.manual(ReasonCode.NO_FACE);
        }
        if (faceCount > 1) {
            return Decision.manual(ReasonCode.MULTI_FACE);
        }
        Double threshold = thresholds.get(modelId);
        if (threshold == null) {
            return Decision.manual(ReasonCode.MODEL_MISMATCH); // no calibrated threshold for this model: never guess
        }
        List<Candidate> ranked = candidates.stream()
                .sorted(Comparator.comparingDouble(Candidate::score).reversed())
                .toList();
        if (ranked.isEmpty()) {
            return Decision.manual(ReasonCode.NO_MATCH);
        }
        Candidate best = ranked.get(0);
        Double top2 = ranked.size() > 1 ? ranked.get(1).score() : null; // a one-entry gallery has no runner-up
        if (best.score() < threshold) {
            return Decision.manual(ReasonCode.NO_MATCH, best.score(), top2);
        }
        if (top2 != null && best.score() - top2 < margin) {
            return Decision.manual(ReasonCode.AMBIGUOUS, best.score(), top2); // look-alikes: let a human decide
        }
        return Decision.board(best, top2);
    }
}
