package np.edu.origin.engine;

import java.util.HashSet;
import java.util.Set;

/**
 * How much two term sets overlap, from two plain questions (80% the first, 20% the second):
 *   "how much of everything do we share?"        weighted Jaccard  = shared / union
 *   "how much of MY idea do they already have?"  containment      = shared / mine
 * The second question is what the old formula missed: a small idea sitting inside a
 * big old project looked only half-similar. Every term counts by its weight.
 */
public final class Overlap {

    /** Share of the overlap that comes from containment. Measured on 25 labelled pairs: 0.2 keeps band agreement, 0.5 loses 3 pairs. */
    static final double CONTAINMENT_SHARE = 0.2;

    private Overlap() { }

    public static double of(Set<String> mine, Set<String> theirs, TermStats stats) {
        if (mine.isEmpty() || theirs.isEmpty()) return 0.0;
        Set<String> shared = new HashSet<>(mine);
        shared.retainAll(theirs);
        if (shared.isEmpty()) return 0.0;
        Set<String> union = new HashSet<>(mine);
        union.addAll(theirs);
        double s = stats.weight(shared);
        return (1 - CONTAINMENT_SHARE) * (s / stats.weight(union)) + CONTAINMENT_SHARE * (s / stats.weight(mine));
    }
}
