package poc.gate.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Written before DecisionPolicy. Thresholds are the illustrative placeholders from CLAUDE.md. */
class DecisionPolicyTest {

    private static final String R50 = "r50-buffalo_l";
    private final DecisionPolicy policy = new DecisionPolicy(Map.of(R50, 0.35, "sface-2021dec", 0.363), 0.10);

    /** Candidates P1, P2, ... with the given scores, in the given order. */
    private static List<Candidate> candidates(double... scores) {
        List<Candidate> list = new ArrayList<>();
        for (int i = 0; i < scores.length; i++) {
            list.add(new Candidate(i + 1, "P" + (i + 1), scores[i]));
        }
        return list;
    }

    @ParameterizedTest(name = "faces={0} top1={1} top2={2} -> {3}/{4}")
    @CsvSource({
        // faces, top1, top2, outcome, reason
        "1, 0.80, 0.20, BOARD,  MATCHED",
        "1, 0.35, 0.10, BOARD,  MATCHED",    // exactly T: accepted (top1 >= T)
        "1, 0.3499, 0.10, MANUAL, NO_MATCH", // just below T
        "1, 0.20, 0.10, MANUAL, NO_MATCH",
        "1, 0.60, 0.51, MANUAL, AMBIGUOUS",  // margin 0.09 < 0.10: look-alikes go to a human
        "1, 0.60, 0.49, BOARD,  MATCHED",    // margin 0.11
        "1, 0.34, 0.33, MANUAL, NO_MATCH",   // below T wins over ambiguity
        "0, 0.90, 0.10, MANUAL, NO_FACE",
        "2, 0.90, 0.10, MANUAL, MULTI_FACE",
        "3, 0.90, 0.10, MANUAL, MULTI_FACE",
    })
    void decisionTable(int faces, double top1, double top2, Decision.Outcome outcome, ReasonCode reason) {
        Decision d = policy.decide(faces, R50, candidates(top1, top2));
        assertThat(d.outcome()).isEqualTo(outcome);
        assertThat(d.reason()).isEqualTo(reason);
    }

    @Test
    void boardCarriesTheBestCandidateAndBothScores() {
        Decision d = policy.decide(1, R50, candidates(0.40, 0.90, 0.10)); // unsorted input
        assertThat(d.outcome()).isEqualTo(Decision.Outcome.BOARD);
        assertThat(d.profileId()).isEqualTo("P2");
        assertThat(d.entryId()).isEqualTo(2L);
        assertThat(d.top1()).isEqualTo(0.90);
        assertThat(d.top2()).isEqualTo(0.40);
    }

    @Test
    void manualDecisionsNeverRevealAnIdentityButKeepScoresForMetrics() {
        Decision d = policy.decide(1, R50, candidates(0.60, 0.55));
        assertThat(d.reason()).isEqualTo(ReasonCode.AMBIGUOUS);
        assertThat(d.profileId()).isNull();
        assertThat(d.entryId()).isNull();
        assertThat(d.top1()).isEqualTo(0.60);
        assertThat(d.top2()).isEqualTo(0.55);
    }

    @Test
    void emptyGalleryIsNoMatch() {
        Decision d = policy.decide(1, R50, List.of());
        assertThat(d.reason()).isEqualTo(ReasonCode.NO_MATCH);
        assertThat(d.top1()).isNull();
    }

    @Test
    void singleEntryGalleryHasNoMarginRule() {
        Decision d = policy.decide(1, R50, candidates(0.50));
        assertThat(d.outcome()).isEqualTo(Decision.Outcome.BOARD);
        assertThat(d.top2()).isNull();
    }

    @Test
    void modelWithoutConfiguredThresholdNeverMatches() {
        Decision d = policy.decide(1, "some-new-model", candidates(0.99, 0.10));
        assertThat(d.outcome()).isEqualTo(Decision.Outcome.MANUAL);
        assertThat(d.reason()).isEqualTo(ReasonCode.MODEL_MISMATCH);
    }

    @Test
    void thresholdIsPerModel() {
        // 0.36 passes R50 (T=0.35) but not SFace (T=0.363)
        assertThat(policy.decide(1, R50, candidates(0.36)).outcome()).isEqualTo(Decision.Outcome.BOARD);
        assertThat(policy.decide(1, "sface-2021dec", candidates(0.36)).reason()).isEqualTo(ReasonCode.NO_MATCH);
    }

    @Test
    void toManualDropsIdentityButKeepsScores() {
        Decision board = policy.decide(1, R50, candidates(0.80, 0.20));
        Decision rejected = board.toManual(ReasonCode.IDP_REJECTED);
        assertThat(rejected.outcome()).isEqualTo(Decision.Outcome.MANUAL);
        assertThat(rejected.reason()).isEqualTo(ReasonCode.IDP_REJECTED);
        assertThat(rejected.profileId()).isNull();
        assertThat(rejected.top1()).isEqualTo(0.80);
    }
}
