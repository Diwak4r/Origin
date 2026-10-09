package np.edu.origin.engine;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * How many past projects use each term, and the weight that follows from it.
 * A term used by many projects ("library") says little when two ideas share it;
 * a rare term ("banker") says a lot. weight = 1 / (1 + ln(1 + uses)), so a term
 * nobody used weighs 1.0 and a term used by 9 projects weighs about 0.30.
 */
public final class TermStats {

    /** No history: every term weighs 1.0, which gives the plain unweighted overlap. */
    public static final TermStats NONE = new TermStats(Map.of());

    private final Map<String, Integer> uses;

    public TermStats(Map<String, Integer> uses) {
        this.uses = Map.copyOf(uses);
    }

    /** Counts each term once per project. */
    public static TermStats count(Collection<Candidate> pool, Function<Candidate, Set<String>> terms) {
        Map<String, Integer> counts = new HashMap<>();
        for (Candidate c : pool) {
            for (String t : terms.apply(c)) counts.merge(t, 1, Integer::sum);
        }
        return new TermStats(counts);
    }

    public int uses(String term) {
        return uses.getOrDefault(term, 0);
    }

    public double weight(String term) {
        return 1.0 / (1.0 + Math.log(1.0 + uses(term)));
    }

    public double weight(Collection<String> terms) {
        double sum = 0;
        for (String t : terms) sum += weight(t);
        return sum;
    }
}
