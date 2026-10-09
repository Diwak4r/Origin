package np.edu.origin.decision;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

/**
 * One typed question for the engine. It never writes free text back; it only answers
 * a yes/no (noul) with a probability, or places the state on an ordered scale (score).
 */
public final class Question {

    private final JsonObject json;

    private Question(JsonObject json) {
        this.json = json;
    }

    /** Yes/no question. The answer is the probability of "yes", 0.0 to 1.0. */
    public static Question yesNo(String instructions) {
        return new Question(Json.createObjectBuilder()
                .add("type", "noul")
                .add("instructions", instructions)
                .build());
    }

    /** Ordered scale. The answer is a position from 0 to levels.length - 1 (can be fractional). */
    public static Question scale(String instructions, String... levels) {
        JsonArrayBuilder criteria = Json.createArrayBuilder();
        for (String level : levels) criteria.add(level);
        JsonObjectBuilder b = Json.createObjectBuilder()
                .add("type", "score")
                .add("instructions", instructions)
                .add("criteria", criteria);
        return new Question(b.build());
    }

    JsonObject toJson() {
        return json;
    }
}
