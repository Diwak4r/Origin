package np.edu.origin.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static np.edu.origin.engine.TestVocab.idea;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchIndexTest {

    private static List<Candidate> pool() {
        return List.of(
                idea(1, "Library System", 1, "library, student", "Issue and return books."),
                idea(2, "Pharmacy Stock", 1, "medicine, inventory, billing", "Track medicine stock and bills."),
                idea(3, "Blood Finder", 2, "blood, notification", "Find donors of a blood group."),
                idea(4, "Attendance QR", 2, "attendance, student", "Scan a code to mark presence."),
                idea(5, "Hospital Token", 1, "hospital, notification", "Digital tokens for patients."),
                idea(6, "Farm Prices", 2, "farmer, market-price, sms", "Daily prices by SMS."));
    }

    @Test
    void indexGivesTheSameTopMatchesAsScoringEveryProject() {
        MatchIndex index = new MatchIndex(pool());
        SimilarityEngine brute = SimilarityEngine.learnFrom(pool());
        List<Candidate> ideas = List.of(
                idea(0, "Book Depot", 1, "books, lending, student", "Students borrow books."),
                idea(0, "Clinic Queue", 1, "hospital, notification", "Patients wait for a doctor."),
                idea(0, "Gym Planner", 2, "fitness, workout", "Plan repetitions and rest."));
        for (Candidate me : ideas) {
            List<Breakdown> expected = new ArrayList<>(brute.rank(me, pool(), 3).stream()
                    .filter(b -> b.ruleScore() > 0).toList());
            List<Breakdown> actual = index.rank(me, 3);
            assertEquals(expected.size(), actual.size(), me.title());
            for (int i = 0; i < expected.size(); i++) {
                assertEquals(expected.get(i).other().id(), actual.get(i).other().id(), me.title());
                assertEquals(expected.get(i).ruleScore(), actual.get(i).ruleScore(), 1e-12, me.title());
            }
        }
    }

    @Test
    void anIdeaSharingNothingGetsNoMatchesAndNoCandidates() {
        MatchIndex index = new MatchIndex(pool());
        Candidate gym = idea(0, "Gym Planner", 2, "fitness, workout", "Plan repetitions and rest.");
        assertTrue(index.rank(gym, 5).isEmpty());
        assertEquals(0, index.candidateCount(gym));
    }

    @Test
    void onlyProjectsSharingATermAreScored() {
        MatchIndex index = new MatchIndex(pool());
        Candidate me = idea(0, "Clinic Queue", 1, "hospital, notification", "Patients wait.");
        // hospital: project 5; notification: projects 3 and 5
        assertEquals(2, index.candidateCount(me));
        assertEquals(6, index.size());
    }

    @Test
    void emptyPoolGivesEmptyResults() {
        MatchIndex index = new MatchIndex(List.of());
        assertTrue(index.rank(idea(0, "Anything Goes", 1, "library, student", "Books."), 5).isEmpty());
    }
}
