package np.edu.origin.engine;

/** Tags describe what a project does, and function does not change when the title is renamed. */
public class TagScorer implements Scorer {

    private final TermStats stats;

    public TagScorer(TermStats stats) {
        this.stats = stats;
    }

    @Override public String name()   { return "Tags"; }
    @Override public double weight() { return 0.45; }

    @Override
    public double score(Candidate idea, Candidate other) {
        return Overlap.of(idea.tags(), other.tags(), stats);
    }
}
