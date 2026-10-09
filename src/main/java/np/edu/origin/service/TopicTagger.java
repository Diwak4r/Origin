package np.edu.origin.service;

import np.edu.origin.model.Topic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds which syllabus topics a piece of text mentions. The text is cut into lower-case words and
 * every run of two or three neighbouring words is also joined with a hyphen, so "round robin"
 * matches the keyword "round-robin" and "Banker's algorithm" matches "bankers-algorithm".
 * A keyword matches a word when they are equal; a long single-word keyword also matches longer
 * forms of the word ("thread" matches "threads"). Nothing cleverer than that, by design.
 */
public final class TopicTagger {

    private static final int MIN_PREFIX_LENGTH = 6;

    private final List<Topic> topics;

    public TopicTagger(List<Topic> topics) {
        this.topics = topics;
    }

    public Set<Integer> detect(String... texts) {
        List<String> tokens = tokens(String.join(" ", texts));
        Set<String> exact = new LinkedHashSet<>(tokens);
        Set<Integer> found = new LinkedHashSet<>();
        for (Topic t : topics) {
            for (String kw : t.getKeywords()) {
                if (matches(kw, exact, tokens)) {
                    found.add(t.getId());
                    break;
                }
            }
        }
        return found;
    }

    private static boolean matches(String kw, Set<String> exact, List<String> tokens) {
        if (exact.contains(kw)) return true;
        if (kw.length() < MIN_PREFIX_LENGTH || kw.contains("-")) return false;
        for (String tok : tokens) {
            if (tok.startsWith(kw)) return true;
        }
        return false;
    }

    /** Words, plus every pair and triple of neighbouring words joined with a hyphen. */
    static List<String> tokens(String text) {
        String[] words = text.toLowerCase().replace("'", "").replace("’", "").split("[^a-z0-9]+");
        List<String> clean = new ArrayList<>();
        for (String w : words) if (!w.isEmpty()) clean.add(w);
        List<String> out = new ArrayList<>(clean);
        for (int i = 0; i + 1 < clean.size(); i++) {
            out.add(clean.get(i) + "-" + clean.get(i + 1));
            if (i + 2 < clean.size()) out.add(clean.get(i) + "-" + clean.get(i + 1) + "-" + clean.get(i + 2));
        }
        return out;
    }
}
