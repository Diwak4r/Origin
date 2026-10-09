package np.edu.origin.decision;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/** The engine's reply: one typed answer per question id. */
public final class Answers {

    private final JsonObject answers;
    private final String model;

    Answers(JsonObject reply) throws EngineException {
        JsonValue a = reply.get("answers");
        if (a == null || a.getValueType() != JsonValue.ValueType.OBJECT) {
            throw new EngineException("Reply has no answers object");
        }
        this.answers = a.asJsonObject();
        this.model = reply.getString("model", "unknown");
    }

    /** Probability of "yes" for a yes/no question. */
    public double yes(String id) throws EngineException {
        return field(id, "noul");
    }

    /** Position on the scale of a scale question, 0 = first level. */
    public double level(String id) throws EngineException {
        return field(id, "score");
    }

    public String model() {
        return model;
    }

    private double field(String id, String name) throws EngineException {
        JsonObject one = answers.getJsonObject(id);
        if (one == null || !one.containsKey(name)) {
            throw new EngineException("No '" + name + "' answer for question " + id);
        }
        return one.getJsonNumber(name).doubleValue();
    }
}
