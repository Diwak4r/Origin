package np.edu.origin.engine;

/**
 * The explainable result of comparing the idea with one other project.
 * Every number here can be recomputed by hand from the two tag and keyword sets.
 */
public record Breakdown(Candidate other, double tagScore, double keywordScore, double typeScore, double ruleScore) {

    /** Blends in the background concept judgement when it is available: 60% rules, 40% concept. */
    public static double blend(double ruleScore, Double conceptScore) {
        if (conceptScore == null) return ruleScore;
        return 0.6 * ruleScore + 0.4 * conceptScore;
    }
}
