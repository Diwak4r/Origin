package np.edu.origin.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A pool of past projects with a book-style index: term -> the projects that contain it.
 * A check looks up the idea's own terms and scores only those projects instead of the whole
 * pool. A project sharing no term scores zero anyway, so the result is the same, only faster.
 * The index also owns the engine whose term weights were learned from this pool.
 */
public final class MatchIndex {

    private final List<Candidate> pool;
    private final Map<String, List<Integer>> byTerm = new HashMap<>();
    private final Map<Integer, Candidate> byId = new HashMap<>();
    private final SimilarityEngine engine;

    public MatchIndex(List<Candidate> pool) {
        this.pool = List.copyOf(pool);
        this.engine = SimilarityEngine.learnFrom(this.pool);
        for (int i = 0; i < this.pool.size(); i++) {
            Candidate c = this.pool.get(i);
            byId.put(c.id(), c);
            for (String term : terms(c)) byTerm.computeIfAbsent(term, k -> new ArrayList<>()).add(i);
        }
    }

    public SimilarityEngine engine() {
        return engine;
    }

    /** The pooled project with this database id, or null. */
    public Candidate candidate(int id) {
        return byId.get(id);
    }

    public int size() {
        return pool.size();
    }

    /** Projects that share at least one term with the idea. */
    public int candidateCount(Candidate idea) {
        return shortlist(idea).size();
    }

    /** The closest {@code limit} projects that share something with the idea, best first. */
    public List<Breakdown> rank(Candidate idea, int limit) {
        List<Breakdown> out = new ArrayList<>();
        for (int i : shortlist(idea)) out.add(engine.compare(idea, pool.get(i)));
        out.sort(Comparator.comparingDouble(Breakdown::ruleScore).reversed());
        return out.subList(0, Math.min(limit, out.size()));
    }

    private Set<Integer> shortlist(Candidate idea) {
        Set<Integer> hits = new LinkedHashSet<>();
        for (String term : terms(idea)) {
            List<Integer> found = byTerm.get(term);
            if (found != null) hits.addAll(found);
        }
        return hits;
    }

    private static Set<String> terms(Candidate c) {
        Set<String> all = new LinkedHashSet<>(c.tags());
        all.addAll(c.keywords());
        return all;
    }
}
