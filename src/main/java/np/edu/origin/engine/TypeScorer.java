package np.edu.origin.engine;

/**
 * Same kind of deliverable (web, mobile, IoT...) or not. A coarse signal, so it counts least,
 * and the engine adds it only when the topics already overlap.
 */
public class TypeScorer implements Scorer {

    @Override public String name()   { return "Type"; }
    @Override public double weight() { return 0.20; }

    @Override
    public double score(Candidate idea, Candidate other) {
        return idea.typeId() == other.typeId() ? 1.0 : 0.0;
    }
}
