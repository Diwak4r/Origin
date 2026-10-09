package np.edu.origin.engine;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** A small vocabulary with the same synonym rules as reference.sql, for tests that need no database. */
public final class TestVocab {

    private TestVocab() { }

    public static final Vocabulary VOCAB = new Vocabulary(
            Map.ofEntries(
                    Map.entry("book", "library"), Map.entry("lending", "library"), Map.entry("borrow", "library"),
                    Map.entry("catalog", "library"), Map.entry("reservation", "booking"), Map.entry("reserve", "booking"),
                    Map.entry("clinic", "hospital"), Map.entry("drug", "medicine"), Map.entry("stock", "inventory"),
                    Map.entry("warehouse", "inventory"), Map.entry("invoice", "billing"), Map.entry("bill", "billing"),
                    Map.entry("esewa", "payment"), Map.entry("khalti", "payment"), Map.entry("blood", "blood-donation"),
                    Map.entry("donor", "blood-donation"), Map.entry("forecast", "prediction"),
                    Map.entry("predict", "prediction"), Map.entry("alert", "notification"),
                    Map.entry("alerts", "notification"), Map.entry("presence", "attendance")),
            List.of("library", "booking", "hospital", "medicine", "inventory", "billing", "payment", "blood-donation",
                    "prediction", "notification", "attendance", "student", "sms", "farmer", "market-price"));

    /** Builds a candidate the way the services do: canonical tags plus keywords from title and abstract. */
    public static Candidate idea(int id, String title, int type, String tags, String abstractText) {
        Set<String> t = VOCAB.canonicalTags(tags);
        return new Candidate(id, title, type, t, VOCAB.keywords(title, abstractText));
    }
}
