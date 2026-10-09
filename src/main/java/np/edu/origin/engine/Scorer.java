package np.edu.origin.engine;

import java.util.HashSet;
import java.util.Set;

/**
 * One explainable signal of similarity. The engine holds a list of scorers and
 * never needs to know which concrete one it is calling (polymorphism).
 */
public interface Scorer {

    /** Short name shown in the score breakdown. */
    String name();

    /** How much this signal counts in the total. All weights add up to 1.0. */
    double weight();

    /** Similarity between two ideas, from 0.0 (nothing shared) to 1.0 (identical). */
    double score(Candidate idea, Candidate other);

    /** Jaccard index |A ∩ B| / |A ∪ B|, shared by the set-based scorers. */
    static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) inter.size() / union.size();
    }
}
