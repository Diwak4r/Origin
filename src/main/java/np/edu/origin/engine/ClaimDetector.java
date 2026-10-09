package np.edu.origin.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Catches buzzword padding. Writing "AI" or "prediction" in a title makes an old idea look
 * new, so every such claim must be backed by at least one supporting word in the abstract.
 */
public class ClaimDetector {

    private record Claim(String label, Set<String> triggers, Set<String> support) { }

    private static final List<Claim> CLAIMS = List.of(
            new Claim("AI / machine learning", Set.of("ai", "ml", "machine", "intelligence", "intelligent", "neural"),
                    Set.of("dataset", "train", "trained", "training", "model", "algorithm", "classify", "classifier",
                           "classification", "regression", "features", "accuracy")),
            new Claim("Prediction", Set.of("prediction", "predict", "predictive", "forecast", "forecasting"),
                    Set.of("data", "dataset", "historical", "history", "model", "regression", "trend", "average",
                           "algorithm", "records", "past")),
            new Claim("IoT / hardware", Set.of("iot", "sensor", "sensors", "arduino"),
                    Set.of("sensor", "arduino", "esp32", "esp8266", "raspberry", "microcontroller", "rfid",
                           "device", "module", "reading", "readings", "pump", "relay")),
            new Claim("Blockchain", Set.of("blockchain", "crypto", "ledger"),
                    Set.of("hash", "block", "chain", "consensus", "contract", "node", "immutable")),
            new Claim("Recommendation", Set.of("recommendation", "recommend", "recommender"),
                    Set.of("rating", "ratings", "history", "preference", "preferences", "similar", "collaborative",
                           "interest", "interests")),
            new Claim("Chatbot", Set.of("chatbot", "bot"),
                    Set.of("intent", "question", "questions", "answer", "answers", "faq", "reply", "dialog")));

    /** Returns one readable reason per unsupported claim; an empty list means nothing to flag. */
    public List<String> unsupportedClaims(String title, String tags, String abstractText) {
        Set<String> head = Vocabulary.words(title + " " + (tags == null ? "" : tags.replace('-', ' ')));
        Set<String> body = Vocabulary.words(abstractText);
        List<String> reasons = new ArrayList<>();
        for (Claim c : CLAIMS) {
            boolean claimed = c.triggers().stream().anyMatch(head::contains);
            boolean backed = c.support().stream().anyMatch(body::contains);
            if (claimed && !backed) {
                reasons.add(c.label() + " is claimed but the abstract never says how. Mention the data, "
                        + "device or method, or drop the word.");
            }
        }
        return reasons;
    }
}
