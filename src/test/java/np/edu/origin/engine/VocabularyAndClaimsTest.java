package np.edu.origin.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VocabularyAndClaimsTest {

    private final Vocabulary v = TestVocab.VOCAB;
    private final ClaimDetector claims = new ClaimDetector();

    @Test
    void tagsAreCanonicalisedLowercasedAndDeduplicated() {
        assertEquals(Set.of("library", "qr-code"), v.canonicalTags(" Book , QR Code; lending, LIBRARY "));
    }

    @Test
    void unknownTagsAreKeptSoTheVocabularyCanGrow() {
        assertTrue(v.canonicalTags("drone-delivery").contains("drone-delivery"));
    }

    @Test
    void keywordsDropFillerWordsAndFoldPlurals() {
        Set<String> k = v.keywords("Online Library Management System", "Students borrow books from libraries.");
        assertTrue(k.contains("library"));
        assertTrue(k.contains("student"));
        assertFalse(k.contains("system"));
        assertFalse(k.contains("online"));
        assertFalse(k.contains("management"));
    }

    @Test
    void singularHandlesCommonEndings() {
        assertEquals("library", Vocabulary.singular("libraries"));
        assertEquals("book", Vocabulary.singular("books"));
        assertEquals("class", Vocabulary.singular("class"));
        assertEquals("status", Vocabulary.singular("status"));
    }

    @Test
    void buzzwordWithoutSubstanceIsFlagged() {
        List<String> r = claims.unsupportedClaims("AI Library Prediction System", "library",
                "Students issue and return books at the counter.");
        assertEquals(2, r.size(), r.toString());
    }

    @Test
    void claimBackedByTheAbstractPasses() {
        List<String> r = claims.unsupportedClaims("Crop Price Prediction", "market-price",
                "Uses five years of historical Kalimati prices and a moving average model to forecast next week.");
        assertTrue(r.isEmpty(), r.toString());
    }

    @Test
    void wordsThatMerelyContainAiAreNotClaims() {
        assertTrue(claims.unsupportedClaims("Rain Gauge Maintenance Planner", "e-learning, water",
                "Available staff plan maintenance of rain gauges.").isEmpty());
    }
}
