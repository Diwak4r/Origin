package np.edu.origin.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Weighted sum of the scorers: tags 0.45, keywords 0.35, type 0.20. The type bonus is added only
 * when tags or keywords share something, so two unrelated web apps do not start at 20%.
 * The same method scores the idea against past projects and against this semester's class
 * proposals, so there is one scoring path to explain and to test.
 */
public class SimilarityEngine {

    private final TermStats tagStats;
    private final TermStats wordStats;
    private final TagScorer tags;
    private final KeywordScorer keywords;
    private final TypeScorer type = new TypeScorer();
    private final List<Scorer> scorers;

    /** Without history every term weighs the same. Used where no corpus is at hand. */
    public SimilarityEngine() {
        this(TermStats.NONE, TermStats.NONE);
    }

    public SimilarityEngine(TermStats tagStats, TermStats wordStats) {
        this.tagStats = tagStats;
        this.wordStats = wordStats;
        this.tags = new TagScorer(tagStats);
        this.keywords = new KeywordScorer(wordStats);
        this.scorers = List.of(tags, keywords, type);
        double total = scorers.stream().mapToDouble(Scorer::weight).sum();
        if (Math.abs(total - 1.0) > 1e-9) {
            throw new IllegalStateException("Scorer weights must add up to 1.0, found " + total);
        }
    }

    /** An engine whose term weights come from how often each term appears in this pool. */
    public static SimilarityEngine learnFrom(Collection<Candidate> pool) {
        return new SimilarityEngine(TermStats.count(pool, Candidate::tags), TermStats.count(pool, Candidate::keywords));
    }

    public List<Scorer> scorers() {
        return scorers;
    }

    public Breakdown compare(Candidate idea, Candidate other) {
        double t = tags.score(idea, other);
        double k = keywords.score(idea, other);
        double y = type.score(idea, other);
        double total = tags.weight() * t + keywords.weight() * k;
        if (t > 0 || k > 0) total += type.weight() * y;
        return new Breakdown(other, t, k, y, total);
    }

    /** Scores the whole pool and returns the closest {@code limit} projects, best first. */
    public List<Breakdown> rank(Candidate idea, Collection<Candidate> pool, int limit) {
        List<Breakdown> all = new ArrayList<>();
        for (Candidate other : pool) {
            all.add(compare(idea, other));
        }
        all.sort(Comparator.comparingDouble(Breakdown::ruleScore).reversed());
        return all.subList(0, Math.min(limit, all.size()));
    }

    /** What share of the idea the first {@code n} matches cover together. {@code top} must be best first. */
    public Coverage coverage(Candidate idea, List<Breakdown> top, int n) {
        Set<String> tagPool = new LinkedHashSet<>();
        Set<String> wordPool = new LinkedHashSet<>();
        int contributing = 0;
        for (Breakdown b : top.subList(0, Math.min(n, top.size()))) {
            Candidate other = b.other();
            boolean adds = idea.tags().stream().anyMatch(other.tags()::contains)
                    || idea.keywords().stream().anyMatch(other.keywords()::contains);
            if (adds) contributing++;
            tagPool.addAll(other.tags());
            wordPool.addAll(other.keywords());
        }
        double total = tagStats.weight(idea.tags()) + wordStats.weight(idea.keywords());
        if (total == 0) return new Coverage(0, 0);
        double covered = 0;
        for (String t : idea.tags()) if (tagPool.contains(t)) covered += tagStats.weight(t);
        for (String w : idea.keywords()) if (wordPool.contains(w)) covered += wordStats.weight(w);
        return new Coverage(covered / total, contributing);
    }

    /** The shared / yours-only / theirs-only terms of one match: tags first, then words, rarest first. */
    public Story story(Candidate idea, Candidate other) {
        Set<String> mine = new LinkedHashSet<>(idea.tags());
        mine.addAll(idea.keywords());
        Set<String> theirs = new LinkedHashSet<>(other.tags());
        theirs.addAll(other.keywords());
        List<String> shared = new ArrayList<>(mine);
        shared.retainAll(theirs);
        List<String> yoursOnly = new ArrayList<>(mine);
        yoursOnly.removeAll(theirs);
        List<String> theirsOnly = new ArrayList<>(theirs);
        theirsOnly.removeAll(mine);
        // Tags say what a project does, so they come first; plain words follow, rarest first.
        Set<String> tagTerms = new LinkedHashSet<>(idea.tags());
        tagTerms.addAll(other.tags());
        Comparator<String> order = Comparator.<String, Boolean>comparing(t -> !tagTerms.contains(t))
                .thenComparing(Comparator.comparingDouble(this::rarity).reversed())
                .thenComparing(Comparator.naturalOrder());
        for (List<String> list : List.of(shared, yoursOnly, theirsOnly)) list.sort(order);
        return new Story(shared, yoursOnly, theirsOnly);
    }

    /** A term is only as rare as its commoner use (as a tag or as a word). */
    private double rarity(String term) {
        return Math.min(tagStats.weight(term), wordStats.weight(term));
    }
}
