package np.edu.origin.engine;

/**
 * Meaningful words from the title and abstract. Weighted below tags because
 * words are easy to change, but it still catches two write-ups of the same idea.
 */
public class KeywordScorer implements Scorer {

    private final TermStats stats;

    public KeywordScorer(TermStats stats) {
        this.stats = stats;
    }

    @Override public String name()   { return "Keywords"; }
    @Override public double weight() { return 0.35; }

    @Override
    public double score(Candidate idea, Candidate other) {
        return Overlap.of(idea.keywords(), other.keywords(), stats);
    }
}
