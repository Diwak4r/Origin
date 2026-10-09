package np.edu.origin.engine;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns free text into the canonical words the scorers compare.
 * "Book Lending Portal" and "Library System" both become {library},
 * which is why renaming a project does not lower its similarity.
 */
public class Vocabulary {

    /** Words that say nothing about what a project does. */
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "of", "for", "to", "in", "on", "at", "by", "with", "from", "into",
            "is", "are", "be", "it", "its", "this", "that", "their", "they", "them", "can", "will", "who",
            "which", "when", "where", "each", "any", "all", "one", "per", "so", "as", "has", "have", "not",
            "system", "management", "app", "application", "online", "portal", "based", "platform", "using",
            "project", "smart", "digital", "simple", "software", "website", "site", "web", "mobile", "tool",
            "user", "users", "admin", "module", "nepal", "nepali", "new", "also", "easy", "help", "helps",
            "through", "about", "than", "then", "there", "these", "those", "our", "we", "you", "your", "get",
            "see", "use", "used", "make", "made", "without", "only", "more", "most", "day", "daily");

    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9]+");

    private final Map<String, String> synonyms;   // alias -> canonical tag
    private final Set<String> knownTags;

    public Vocabulary(Map<String, String> synonyms, Collection<String> knownTags) {
        this.synonyms = Map.copyOf(synonyms);
        this.knownTags = Set.copyOf(knownTags);
    }

    /** "Book, QR Code , lending" -> {library, qr-code}. Unknown tags are kept (the vocabulary grows). */
    public Set<String> canonicalTags(String rawTags) {
        Set<String> out = new LinkedHashSet<>();
        if (rawTags == null) return out;
        for (String part : rawTags.split("[,;]")) {
            String tag = part.trim().toLowerCase().replaceAll("\\s+", "-").replaceAll("[^a-z0-9-]", "");
            if (tag.length() < 2 || tag.length() > 40) continue;
            // "books" -> "book" -> library, but only when the singular is a word we know,
            // so a real tag such as "analytics" is never cut down to "analytic".
            if (!knownTags.contains(tag) && !synonyms.containsKey(tag)) {
                String one = singular(tag);
                if (knownTags.contains(one) || synonyms.containsKey(one)) tag = one;
            }
            out.add(canonical(tag));
        }
        return out;
    }

    /** Title + abstract -> meaningful, canonical keywords. */
    public Set<String> keywords(String... texts) {
        Set<String> out = new LinkedHashSet<>();
        for (String text : texts) {
            if (text == null) continue;
            Arrays.stream(NON_WORD.split(text.toLowerCase()))
                  .filter(w -> w.length() > 2 && !STOP_WORDS.contains(w))
                  .map(Vocabulary::singular)
                  .map(this::canonical)
                  .forEach(out::add);
        }
        return out;
    }

    /** Words of the text that are known tags once synonyms are folded: "book lending" -> {library}. */
    public Set<String> tagsFromText(String... texts) {
        Set<String> out = new LinkedHashSet<>();
        for (String k : keywords(texts)) {
            if (knownTags.contains(k)) out.add(k);
        }
        return out;
    }

    /** Plain lowercase words with no filtering. The claim detector reads these. */
    public static Set<String> words(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) return out;
        for (String w : NON_WORD.split(text.toLowerCase())) {
            if (!w.isBlank()) out.add(w);
        }
        return out;
    }

    private String canonical(String word) {
        return synonyms.getOrDefault(word, word);
    }

    /** Very small plural folding: "libraries" -> "library", "books" -> "book". */
    static String singular(String w) {
        if (w.length() > 4 && w.endsWith("ies")) return w.substring(0, w.length() - 3) + "y";
        if (w.length() > 4 && w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us")) {
            return w.substring(0, w.length() - 1);
        }
        return w;
    }
}
