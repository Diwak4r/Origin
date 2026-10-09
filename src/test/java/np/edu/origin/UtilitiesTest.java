package np.edu.origin;

import jakarta.json.Json;
import np.edu.origin.decision.Answers;
import np.edu.origin.decision.DecisionClient;
import np.edu.origin.decision.EngineException;
import np.edu.origin.decision.Question;
import np.edu.origin.service.CsvWriter;
import np.edu.origin.service.PasswordHasher;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtilitiesTest {

    @Test
    void passwordHashIsSaltedAndVerifies() {
        String s1 = PasswordHasher.newSalt();
        String s2 = PasswordHasher.newSalt();
        String h1 = PasswordHasher.hash("Origin@2026", s1);
        assertEquals(64, h1.length());
        assertNotEquals(h1, PasswordHasher.hash("Origin@2026", s2), "same password, different salt, different hash");
        assertTrue(PasswordHasher.matches("Origin@2026", s1, h1));
        assertFalse(PasswordHasher.matches("origin@2026", s1, h1));
    }

    @Test
    void csvQuotesCommasAndNeutralisesFormulas() throws Exception {
        StringWriter out = new StringWriter();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("title", "Library, Lending \"Desk\"");
        row.put("note", "=HYPERLINK(\"x\")");
        CsvWriter.write(out, List.of("title", "note"), List.of(row));
        String[] lines = out.toString().split("\r\n");
        assertEquals("title,note", lines[0]);
        assertEquals("\"Library, Lending \"\"Desk\"\"\",\"'=HYPERLINK(\"\"x\"\")\"", lines[1]);
    }

    @Test
    void decisionClientSendsTypedQuestionsAndCachesTheReply() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        StringBuilder sent = new StringBuilder();
        DecisionClient.Transport fake = body -> {
            calls.incrementAndGet();
            sent.append(body);
            return "{\"model\":\"jev-1.13.0\",\"answers\":{\"same\":{\"type\":\"noul\",\"noul\":0.91},"
                 + "\"level\":{\"type\":\"score\",\"score\":3.0,\"confidence\":1.0}}}";
        };
        Map<String, String> store = new HashMap<>();
        DecisionClient.Cache cache = new DecisionClient.Cache() {
            public String get(String key) { return store.get(key); }
            public void put(String key, String json, String model) { store.put(key, json); }
        };
        DecisionClient client = new DecisionClient(fake, cache, "jev-latest", true);
        Map<String, Question> q = new LinkedHashMap<>();
        q.put("same", Question.yesNo("Same project?"));
        q.put("level", Question.scale("How new?", "old", "same", "bit new", "new", "brand new"));

        Answers a = client.ask(Json.createObjectBuilder().add("idea", "x").build(), q);
        assertEquals(0.91, a.yes("same"), 1e-9);
        assertEquals(3.0, a.level("level"), 1e-9);
        assertTrue(sent.toString().contains("\"type\":\"noul\""));
        assertTrue(sent.toString().contains("\"criteria\":[\"old\""));

        client.ask(Json.createObjectBuilder().add("idea", "x").build(), q);
        assertEquals(1, calls.get(), "the second identical question must come from the cache");
    }

    @Test
    void decisionClientWithoutKeyRefusesInsteadOfCalling() {
        DecisionClient off = new DecisionClient(body -> { throw new AssertionError("must not call"); },
                new DecisionClient.Cache() {
                    public String get(String key) { return null; }
                    public void put(String key, String json, String model) { }
                }, "jev-latest", false);
        assertFalse(off.isEnabled());
        assertThrows(EngineException.class, () -> off.ask(Json.createObjectBuilder().build(), Map.of()));
    }
}
