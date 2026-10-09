package np.edu.origin.decision;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import np.edu.origin.config.AppConfig;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

/**
 * Client for the background decision engine (TypeSafe's Jev model, a "System One" model that
 * returns calibrated probabilities instead of generated text).
 *
 * One call = one POST of {model, state, questions}. Replies are cached by a SHA-256 of the
 * request body, so asking the same thing twice costs nothing and works offline afterwards.
 */
public class DecisionClient {

    /** How the request body reaches the engine. Tests plug in a fake. */
    public interface Transport {
        String post(String body) throws EngineException;
    }

    /** Where answers are remembered. Production uses the engine_cache table. */
    public interface Cache {
        String get(String key);
        void put(String key, String answerJson, String model);
    }

    private final Transport transport;
    private final Cache cache;
    private final String model;
    private final boolean enabled;

    public DecisionClient(Transport transport, Cache cache, String model, boolean enabled) {
        this.transport = transport;
        this.cache = cache;
        this.model = model;
        this.enabled = enabled;
    }

    /** Client wired from config/origin.properties. Disabled when no key is configured. */
    public static DecisionClient fromConfig(Cache cache) {
        String key = AppConfig.engineKey();
        return new DecisionClient(new HttpTransport(AppConfig.engineUrl(), key), cache,
                AppConfig.engineModel(), !key.isBlank());
    }

    /** A client that never calls the engine. Origin uses it while Jev is switched off. */
    public static DecisionClient disabled() {
        return new DecisionClient(body -> { throw new EngineException("Decision engine is switched off"); },
                new Cache() {
                    public String get(String key) { return null; }
                    public void put(String key, String answerJson, String model) { }
                }, "off", false);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Asks every question about the same state in one round trip. */
    public Answers ask(JsonObject state, Map<String, Question> questions) throws EngineException {
        if (!enabled) throw new EngineException("Decision engine is not configured");
        JsonObjectBuilder q = Json.createObjectBuilder();
        for (Map.Entry<String, Question> e : questions.entrySet()) {
            q.add(e.getKey(), e.getValue().toJson());
        }
        String body = Json.createObjectBuilder()
                .add("model", model)
                .add("state", state)
                .add("questions", q)
                .build().toString();

        String key = sha256(body);
        String cached = cache.get(key);
        if (cached != null) return new Answers(parse(cached));

        String reply = transport.post(body);
        Answers answers = new Answers(parse(reply));
        cache.put(key, reply, answers.model());
        return answers;
    }

    private static JsonObject parse(String json) throws EngineException {
        try (JsonReader r = Json.createReader(new StringReader(json))) {
            return r.readObject();
        } catch (RuntimeException e) {
            throw new EngineException("Reply is not valid JSON", e);
        }
    }

    static String sha256(String text) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Real HTTPS transport using java.net.http. */
    static final class HttpTransport implements Transport {
        private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        private final String url;
        private final String key;

        HttpTransport(String url, String key) {
            this.url = url;
            this.key = key;
        }

        @Override
        public String post(String body) throws EngineException {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            try {
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (res.statusCode() != 200) {
                    String snippet = res.body().length() > 200 ? res.body().substring(0, 200) : res.body();
                    throw new EngineException("HTTP " + res.statusCode() + ": " + snippet);
                }
                return res.body();
            } catch (IOException e) {
                throw new EngineException("Network error: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EngineException("Interrupted", e);
            }
        }
    }
}
