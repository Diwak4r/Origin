package np.edu.origin.service;

import np.edu.origin.model.Topic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuggesterLogicTest {

    private static final Topic BANKER = new Topic(1, "OS", 6, "Deadlock and Banker's algorithm", false,
            List.of("deadlock", "bankers-algorithm", "safe-state"));
    private static final Topic RR = new Topic(2, "OS", 2, "Process scheduling", false,
            List.of("fcfs", "round-robin", "cpu-scheduling"));
    private static final Topic THREADS = new Topic(3, "JAVA", 1, "Multithreading", false,
            List.of("thread", "threads", "multithreading"));
    private static final Topic DISK = new Topic(4, "OS", 5, "Disk scheduling", false,
            List.of("disk-scheduling", "sstf", "c-scan"));
    private static final Topic JDBC = new Topic(5, "JAVA", 5, "JDBC", true, List.of("jdbc", "mysql", "database"));
    private static final Topic JSP = new Topic(6, "JAVA", 8, "Servlet and JSP", true, List.of("servlet", "jsp"));
    private static final Topic TRIGGERS = new Topic(7, "DBMS", 4, "Triggers", false, List.of("triggers"));
    private static final List<Topic> ALL = List.of(BANKER, RR, THREADS, DISK, JDBC, JSP, TRIGGERS);

    private final TopicTagger tagger = new TopicTagger(ALL);

    @Test
    void phrasesAndApostrophesMatchHyphenatedKeywords() {
        assertEquals(Set.of(1), tagger.detect("Allocate blankets with the Banker's algorithm"));
        assertEquals(Set.of(2), tagger.detect("A round robin queue for letters"));
    }

    @Test
    void longKeywordsMatchLongerFormsOfTheWord() {
        assertTrue(tagger.detect("Worker threads send the messages").contains(3));
        assertTrue(tagger.detect("Multithreading in the sender").contains(3));
    }

    @Test
    void everydayWordsDoNotFalselyMatch() {
        assertTrue(tagger.detect("Scan a QR code to look at a view of the page").isEmpty());
        assertTrue(tagger.detect("A library with a login page").isEmpty());
        assertTrue(tagger.detect("").isEmpty());
    }

    @Test
    void twoTopicsInOneTextAreBothFound() {
        assertEquals(Set.of(1, 7), tagger.detect("Deadlock safe-state check and triggers"));
    }

    private static SyllabusService.Snapshot snapshot(Map<Long, Integer> pairs, Map<Integer, Integer> usage, int projects) {
        return new SyllabusService.Snapshot(ALL, usage, pairs, projects);
    }

    @Test
    void pairKeyIgnoresOrder() {
        assertEquals(SyllabusService.pairKey(2, 9), SyllabusService.pairKey(9, 2));
    }

    @Test
    void untouchedFallsAsMoreProjectsUsedThePair() {
        assertEquals(1.0, IdeaService.untouched(0), 1e-9);
        assertEquals(0.5, IdeaService.untouched(1), 1e-9);
        assertEquals(0.25, IdeaService.untouched(3), 1e-9);
    }

    @Test
    void coverageCountsUnitsAndStopsAtOne() {
        assertEquals(0.0, IdeaService.coverage(0), 1e-9);
        assertEquals(0.5, IdeaService.coverage(2), 1e-9);
        assertEquals(1.0, IdeaService.coverage(4), 1e-9);
        assertEquals(1.0, IdeaService.coverage(9), 1e-9);
    }

    @Test
    void evidenceUsesTheBusiestPairAndCountsDistinctUnits() {
        Map<Long, Integer> pairs = Map.of(SyllabusService.pairKey(1, 2), 0, SyllabusService.pairKey(1, 3), 4,
                SyllabusService.pairKey(2, 3), 1);
        IdeaService.Evidence ev = IdeaService.evidence(Set.of(1, 2, 3), snapshot(pairs, Map.of(), 100));
        assertEquals(4, ev.pairUses());
        assertTrue(ev.pairLabel().contains("Banker") && ev.pairLabel().contains("Multithreading"), ev.pairLabel());
        assertEquals(3, ev.units());
        assertEquals(2, ev.courses());
        assertFalse(ev.crudOnly());
    }

    @Test
    void aSingleTopicUsesItsOwnCount() {
        IdeaService.Evidence ev = IdeaService.evidence(Set.of(4), snapshot(Map.of(), Map.of(4, 6), 100));
        assertEquals(6, ev.pairUses());
        assertEquals("Disk scheduling", ev.pairLabel());
    }

    @Test
    void anIdeaWhoseTopicsAreAllCrudIsFlaggedAndCapped() {
        IdeaService.Evidence crud = IdeaService.evidence(Set.of(5, 6), snapshot(Map.of(), Map.of(), 100));
        assertTrue(crud.crudOnly());
        assertEquals(0, crud.units());
        double s = IdeaService.strength(crud, 1.0, 1.0);
        assertTrue(s <= IdeaService.CRUD_STRENGTH_CAP + 1e-9, "capped: " + s);

        IdeaService.Evidence real = IdeaService.evidence(Set.of(1, 2, 3, 7), snapshot(Map.of(), Map.of(), 100));
        assertFalse(real.crudOnly());
        assertTrue(IdeaService.strength(real, 0.7, 0.6) > s, "a real idea outranks plain CRUD");
    }

    @Test
    void noTopicsAtAllCountsAsPlainCrud() {
        IdeaService.Evidence none = IdeaService.evidence(Set.of(), snapshot(Map.of(), Map.of(), 10));
        assertTrue(none.crudOnly());
        assertNull(none.pairLabel());
    }

    @Test
    void strengthUsesTheFourWeightsFromTheSpec() {
        assertEquals(1.0, IdeaService.W_UNTOUCHED + IdeaService.W_COVERAGE + IdeaService.W_FEASIBILITY + IdeaService.W_IMPACT, 1e-9);
        IdeaService.Evidence ev = new IdeaService.Evidence(0, "A + B", 4, 2, false, "A, B");
        // untouched 1.0, coverage 1.0, feasibility 0.7, impact 0.6
        assertEquals(0.40 + 0.30 + 0.20 * 0.7 + 0.10 * 0.6, IdeaService.strength(ev, 0.7, 0.6), 1e-9);
    }
}
