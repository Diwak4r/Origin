package np.edu.origin;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import np.edu.origin.config.AppConfig;
import np.edu.origin.dao.AdminDao;
import np.edu.origin.dao.CheckDao;
import np.edu.origin.dao.CorpusDao;
import np.edu.origin.dao.GapDao;
import np.edu.origin.dao.IdeaDao;
import np.edu.origin.dao.LookupDao;
import np.edu.origin.dao.ProposalDao;
import np.edu.origin.dao.SyllabusDao;
import np.edu.origin.dao.UserDao;
import np.edu.origin.db.Database;
import np.edu.origin.db.Seeder;
import np.edu.origin.db.SyllabusSeeder;
import np.edu.origin.decision.DecisionClient;
import np.edu.origin.model.CheckView;
import np.edu.origin.model.IdeaInput;
import np.edu.origin.model.MatchView;
import np.edu.origin.model.ProposalView;
import np.edu.origin.model.Suggestion;
import np.edu.origin.model.User;
import np.edu.origin.service.AuthService;
import np.edu.origin.service.CheckService;
import np.edu.origin.service.IdeaService;
import np.edu.origin.service.RadarService;
import np.edu.origin.service.SyllabusService;
import np.edu.origin.service.ValidationException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests against a real MySQL database named origin_test (dropped and rebuilt each run).
 * They cover the seeding, both scoring pools, the triggers, the stored procedure and the rules of Class Radar.
 * Skipped automatically when MySQL is not reachable.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OriginIntegrationTest {

    private static final String URL = "jdbc:mysql://localhost:3306/origin_test?createDatabaseIfNotExist=true&serverTimezone=Asia/Kathmandu";
    private static final Executor NOW = Runnable::run;

    static LookupDao lookups = new LookupDao();
    static CorpusDao corpus = new CorpusDao();
    static ProposalDao proposals = new ProposalDao();
    static CheckDao checkDao = new CheckDao();
    static IdeaDao ideaDao = new IdeaDao();
    static UserDao users = new UserDao();
    static AdminDao admin = new AdminDao();
    static SyllabusDao syllabusDao = new SyllabusDao();
    static SyllabusService syllabus = new SyllabusService(syllabusDao);
    static DecisionClient off = new DecisionClient(b -> { throw new AssertionError("engine is off"); }, noCache(), "test", false);
    static CheckService checks;
    static RadarService radar;
    static IdeaService ideas;
    static AuthService auth;

    @BeforeAll
    static void buildDatabase() throws Exception {
        try (Connection c = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/?serverTimezone=Asia/Kathmandu", AppConfig.dbUser(), AppConfig.dbPassword());
             Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS origin_test");
        } catch (SQLException e) {
            Assumptions.abort("MySQL not reachable, integration tests skipped: " + e.getMessage());
        }
        Database.configure(URL, AppConfig.dbUser(), AppConfig.dbPassword());
        LookupDao.clearCache();
        checks = new CheckService(lookups, corpus, proposals, checkDao, admin, off, NOW);
        ideas = new IdeaService(lookups, corpus, proposals, ideaDao, new GapDao(), syllabus, syllabusDao, off, NOW);
        radar = new RadarService(lookups, corpus, proposals, checkDao, checks, () -> { });
        auth = new AuthService(users);
        new Seeder(lookups, corpus, ideaDao, users, checks, radar).run();
        new SyllabusSeeder(syllabusDao, corpus).run();
        syllabus.linkNewProjects();
    }

    static DecisionClient.Cache noCache() {
        return new DecisionClient.Cache() {
            public String get(String key) { return null; }
            public void put(String key, String json, String model) { }
        };
    }

    static int count(String sql) throws SQLException {
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    static User sandesh() throws SQLException {
        return auth.login("sandesh@origin.edu", Seeder.DEMO_PASSWORD);
    }

    @Test @Order(1)
    void seedingLoadsCorpusProblemsAndEighteenLockedGroups() throws Exception {
        assertEquals(71, corpus.count());
        assertEquals(42, count("SELECT COUNT(*) FROM problem_bank WHERE source = 'SEED'"));
        assertEquals(18, proposals.count());
        assertEquals(2, count("SELECT COUNT(*) FROM radar_collisions"), "the two library groups clash, and one shares a tag pair");
    }

    @Test @Order(2)
    void triggersKeepCountersAndTheAuditTrail() throws Exception {
        assertEquals(count("SELECT COUNT(*) FROM corpus_project_tags"), count("SELECT SUM(corpus_uses) FROM tags"),
                "trg_cpt_count must count every corpus tag");
        assertEquals(count("SELECT COUNT(*) FROM idea_check_tags"), count("SELECT SUM(check_uses) FROM tags"));
        assertEquals(18, count("SELECT COUNT(*) FROM audit_log WHERE action = 'LOCK'"));
        assertEquals(2, count("SELECT COUNT(*) FROM audit_log WHERE action = 'COLLISION'"));
        assertEquals(18, count("SELECT COUNT(*) FROM problem_bank WHERE source = 'STUDENT'"),
                "trg_check_problem copies each seeded group's problem statement");
        assertTrue(count("SELECT COUNT(*) FROM problem_tags pt JOIN problem_bank pb ON pb.id = pt.problem_id WHERE pb.source = 'STUDENT'") > 0,
                "trg_ict_count copies the tags of student problems");
    }

    @Test @Order(3)
    void loginAcceptsTheRightPasswordOnly() throws Exception {
        assertNotNull(sandesh());
        assertEquals(null, auth.login("sandesh@origin.edu", "wrong-password1"));
        assertEquals(null, auth.login("nobody@origin.edu", Seeder.DEMO_PASSWORD));
    }

    @Test @Order(4)
    void registrationValidatesEveryField() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> auth.register("A", "not-an-email", "short", "7"));
        assertEquals(4, e.getFieldErrors().size(), e.getFieldErrors().toString());
        assertThrows(ValidationException.class, () -> auth.register("Some One", "sandesh@origin.edu", "Password123", ""));
    }

    @Test @Order(5)
    void invalidIdeaIsRejectedWithFieldMessages() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> checks.check(sandesh(), new IdeaInput("Hi", "Too short.", "", "library", 1, 1, 4)));
        assertTrue(e.getFieldErrors().keySet().containsAll(List.of("title", "abstractText", "tags")));
    }

    @Test @Order(6)
    void renamedLibraryIdeaIsCaughtAndCannotBeLocked() throws Exception {
        User u = sandesh();
        int id = checks.check(u, new IdeaInput("Book Depot Portal",
                "Students reserve books online from the college collection and get a reminder before the return date.",
                "", "books, lending, reservation, notification", 1, 1, 4));
        CheckView v = checks.view(id, u);
        assertEquals("DUPLICATE", v.getVerdict(), "score " + v.getFinalScore());
        assertTrue(v.getHistory().get(0).getTitle().toLowerCase().contains("library"));
        assertTrue(v.getClassMatches().stream().anyMatch(m -> m.getOrigin().equals("Group G05")),
                "the class pool finds group G05's library idea");
        assertThrows(ValidationException.class, () -> radar.lock(u, id));
    }

    @Test @Order(7)
    void originalIdeaLocksOnceAndOnlyOnce() throws Exception {
        User u = sandesh();
        int id = checks.check(u, new IdeaInput("Livestock Vaccination Reminder",
                "Dairy farmers register each animal and the app sends an SMS a week before its next vaccination date. "
              + "The ward vet sees which farms are due.",
                "Livestock farmers miss vaccination dates because records are kept in memory.",
                "livestock, vaccination, sms, scheduling", 3, 2, 4));
        CheckView v = checks.view(id, u);
        assertEquals("ORIGINAL", v.getVerdict(), "score " + v.getFinalScore());
        int proposal = radar.lock(u, id);
        assertTrue(proposal > 0);
        assertThrows(ValidationException.class, () -> radar.lock(u, id), "one idea per group per semester");
    }

    @Test @Order(8)
    void buzzwordPaddingIsFlagged() throws Exception {
        User u = sandesh();
        int id = checks.check(u, new IdeaInput("AI Powered Hostel Prediction System",
                "Students book hostel rooms and the warden sees who stays where.", "", "hostel, booking", 1, 1, 4));
        assertTrue(checks.view(id, u).isClaimFlag());
        assertTrue(!checks.view(id, u).getClaimReasons().isEmpty());
    }

    @Test @Order(9)
    void backgroundEngineRefinesTheVerdict() throws Exception {
        // A fake engine that answers "same project" with 0.95 and "claims unsupported" with 0.1.
        DecisionClient fake = new DecisionClient(body -> {
            JsonObject req = Json.createReader(new StringReader(body)).readObject();
            JsonObjectBuilder answers = Json.createObjectBuilder();
            for (Map.Entry<String, JsonValue> q : req.getJsonObject("questions").entrySet()) {
                double p = q.getKey().startsWith("same_") ? 0.95 : 0.1;
                answers.add(q.getKey(), Json.createObjectBuilder().add("type", "noul").add("noul", p));
            }
            return Json.createObjectBuilder().add("model", "fake").add("answers", answers).build().toString();
        }, noCache(), "fake", true);
        CheckService withEngine = new CheckService(lookups, corpus, proposals, checkDao, admin, fake, NOW);
        User u = sandesh();
        int id = withEngine.check(u, new IdeaInput("Hotel Room Finder",
                "Guests look for free hotel rooms in Pokhara and pay a deposit online to hold the room.",
                "", "hotel, booking, payment", 4, 1, 4));
        CheckView v = withEngine.view(id, u);
        assertEquals("DONE", v.getEngineStatus());
        MatchView top = v.getHistory().stream().filter(MatchView::isConceptKnown).findFirst().orElseThrow();
        assertEquals(0.95, top.getConceptScore(), 1e-4);
        assertEquals(0.6 * top.getRuleScore() + 0.4 * 0.95, top.getFinalScore(), 1e-3);
        assertEquals(false, v.isClaimFlag(), "0.1 for unsupported claims must not raise the flag");
    }

    @Test @Order(10)
    void supervisorCorrectionIsRememberedForTheSameIdea() throws Exception {
        User sup = auth.login("supervisor@origin.edu", Seeder.DEMO_PASSWORD);
        User u = sandesh();
        IdeaInput in = new IdeaInput("Campus Book Swap",
                "Students list second-hand textbooks and swap them with juniors at the start of each semester.",
                "", "library, student, exchange", 1, 1, 4);
        int first = checks.check(u, in);
        MatchView m = checks.view(first, u).getHistory().get(0);
        checks.override(sup, first, m.getId(), false);
        assertEquals(0.0, checks.view(first, u).getHistory().stream()
                .filter(x -> x.getId() == m.getId()).findFirst().orElseThrow().getConceptScore(), 1e-9);

        int second = checks.check(u, in);
        MatchView again = checks.view(second, u).getHistory().stream()
                .filter(x -> x.getTargetId() == m.getTargetId()).findFirst().orElseThrow();
        assertEquals(0.0, again.getConceptScore(), 1e-9, "the correction is applied without asking again");
    }

    @Test @Order(12)
    void ideaSuggestionsMarkWhatLockedGroupsTook() throws Exception {
        int n = ideas.refresh();
        assertTrue(n >= 42, "every seed problem gets at least one suggestion, got " + n);
        List<Suggestion> taken = ideaDao.list(null, null, null, true, 500).stream()
                .filter(s -> "TAKEN".equals(s.getStatus())).toList();
        assertTrue(taken.stream().anyMatch(s -> s.getTitle().contains("Livestock Vaccination")),
                "the idea locked in order 7 marks its seed problem TAKEN");
        assertTrue(ideas.list(null, null, null, false).stream().allMatch(Suggestion::isOpen));
    }

    @Test @Order(13)
    void suggestionsCarryTheirSyllabusProofAndMatchAHandCount() throws Exception {
        ideas.refresh();
        assertEquals(41, count("SELECT COUNT(*) FROM syllabus_topics"));
        assertEquals(42, count("SELECT COUNT(DISTINCT problem_id) FROM problem_topics"), "every seeded problem has topics");
        assertEquals(42, count("SELECT COUNT(*) FROM problem_twist"));
        assertTrue(count("SELECT COUNT(DISTINCT project_id) FROM corpus_project_topics") > 0, "past projects are linked to topics");
        assertEquals(count("SELECT COUNT(*) FROM idea_suggestions"), count("SELECT COUNT(*) FROM suggestion_proof"),
                "every suggestion has its proof row");

        // Hand count for every suggestion: the busiest pair of its problem's topics, counted straight from the tables.
        int checked = 0;
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT sg.problem_id, sp.pair_uses, sp.crud_only FROM idea_suggestions sg "
                   + "JOIN suggestion_proof sp ON sp.suggestion_id = sg.id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int problem = rs.getInt(1);
                int claimed = rs.getInt(2);
                List<Integer> topics = new java.util.ArrayList<>();
                try (PreparedStatement p2 = c.prepareStatement("SELECT topic_id FROM problem_topics WHERE problem_id = ? ORDER BY topic_id")) {
                    p2.setInt(1, problem);
                    try (ResultSet r2 = p2.executeQuery()) { while (r2.next()) topics.add(r2.getInt(1)); }
                }
                int best = topics.size() == 1 ? count("SELECT COUNT(*) FROM corpus_project_topics WHERE topic_id = " + topics.get(0)) : 0;
                for (int i = 0; i < topics.size(); i++) {
                    for (int j = i + 1; j < topics.size(); j++) {
                        best = Math.max(best, count("SELECT COUNT(*) FROM corpus_project_topics a JOIN corpus_project_topics b "
                                + "ON a.project_id = b.project_id WHERE a.topic_id = " + topics.get(i) + " AND b.topic_id = " + topics.get(j)));
                    }
                }
                assertEquals(best, claimed, "proof for problem " + problem);
                checked++;
            }
        }
        assertTrue(checked >= 42, "checked " + checked);
    }

    @Test @Order(14)
    void noPlainCrudIdeaAppearsAboveARealOne() throws Exception {
        List<Suggestion> list = ideas.list(null, null, null, true);
        boolean seenCrud = false;
        for (Suggestion s : list) {
            if (s.isCrudOnly()) seenCrud = true;
            else assertTrue(!seenCrud, "a real idea (" + s.getTitle() + ") is listed after a plain CRUD one");
        }
        assertTrue(list.stream().limit(10).noneMatch(Suggestion::isCrudOnly));
        assertTrue(list.stream().filter(s -> s.isOpen()).allMatch(s -> s.getUnits() >= 1 || s.isCrudOnly()));
        assertTrue(list.stream().filter(Suggestion::isOpen).findFirst().orElseThrow().getTwist() != null, "the top idea tells its twist");
    }

    @Test @Order(15)
    void archivingMovesApprovedIdeasIntoTheCorpus() throws Exception {
        User sup = auth.login("supervisor@origin.edu", Seeder.DEMO_PASSWORD);
        ProposalView g02 = proposals.radar(null).stream().filter(p -> p.getGroupCode().equals("G02")).findFirst().orElseThrow();
        proposals.review(g02.getId(), "APPROVED", sup.getId(), "Good problem, clear users.");
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'APPROVED'"), "trg_proposal_reviewed");

        int before = corpus.count();
        int moved = admin.archiveSemester("2027-SPRING", "Spring 2027", sup.getId());
        assertEquals(1, moved);
        assertEquals(before + 1, corpus.count());
        assertEquals(1, count("SELECT COUNT(*) FROM corpus_projects WHERE source = 'ARCHIVED' AND title LIKE 'ORIGIN%'"));
        assertTrue(count("SELECT COUNT(*) FROM corpus_project_tags cpt JOIN corpus_projects cp ON cp.id = cpt.project_id WHERE cp.source = 'ARCHIVED'") >= 3);
        assertEquals(0, proposals.count(), "the new semester starts with an empty radar");
        assertEquals(1, count("SELECT COUNT(*) FROM semesters WHERE is_active"));
    }

    @Test @Order(16)
    void semesterReportProcedureReturnsOneRowPerGroup() throws Exception {
        // After the archive the new semester is empty, so the report is empty too.
        assertEquals(0, admin.semesterReport().size());
        assertEquals("DUPLICATE", scalar("SELECT fn_band(0.72)"));
        assertEquals("SIMILAR", scalar("SELECT fn_band(0.35)"));
        assertEquals("ORIGINAL", scalar("SELECT fn_band(0.10)"));
    }

    @Test @Order(17)
    void lockUsesTheFinalVerdictWhenTheBackgroundCheckHasNotFinished() throws Exception {
        // The fake engine is sure the idea is the same project as its closest match.
        DecisionClient sure = new DecisionClient(body -> {
            JsonObject req = Json.createReader(new StringReader(body)).readObject();
            JsonObjectBuilder answers = Json.createObjectBuilder();
            for (Map.Entry<String, JsonValue> q : req.getJsonObject("questions").entrySet()) {
                double p = q.getKey().startsWith("same_") ? 0.99 : 0.05;
                answers.add(q.getKey(), Json.createObjectBuilder().add("type", "noul").add("noul", p));
            }
            return Json.createObjectBuilder().add("model", "fake").add("answers", answers).build().toString();
        }, noCache(), "fake", true);
        Executor never = task -> { };                       // the background thread never gets to run
        CheckService pending = new CheckService(lookups, corpus, proposals, checkDao, admin, sure, never);
        RadarService lockNow = new RadarService(lookups, corpus, proposals, checkDao, pending, () -> { });
        User u = sandesh();
        int id = pending.check(u, new IdeaInput("Hotel Stay Planner",
                "Travellers compare hotel rooms in Pokhara, book one and pay the deposit online.", "",
                "hotel, booking", 4, 2, 4));
        assertEquals("PENDING", checkDao.find(id).getEngineStatus());
        assertTrue(!"DUPLICATE".equals(checkDao.find(id).getVerdict()), "the rule score alone does not say duplicate");
        ValidationException e = assertThrows(ValidationException.class, () -> lockNow.lock(u, id));
        assertTrue(e.getMessage().contains("already done"), e.getMessage());
        assertEquals("DONE", checkDao.find(id).getEngineStatus());
    }

    @Test @Order(15)
    void targetSemesterMustBeBetweenOneAndEight() {
        ValidationException low = assertThrows(ValidationException.class,
                () -> checks.check(sandesh(), new IdeaInput("Valid Title Here", "A description long enough to pass validation, with detail.", "", "tag, second", 1, 1, 0)));
        assertTrue(low.getFieldErrors().containsKey("targetSemester"));
        ValidationException high = assertThrows(ValidationException.class,
                () -> checks.check(sandesh(), new IdeaInput("Valid Title Here", "A description long enough to pass validation, with detail.", "", "tag, second", 1, 1, 9)));
        assertTrue(high.getFieldErrors().containsKey("targetSemester"));
    }

    @Test @Order(16)
    void corpusSampleStaysWithinItsLimit() throws Exception {
        assertTrue(corpus.sample(5).size() <= 5);
        assertEquals(1, corpus.sample(0).size(), "the limit is clamped to at least 1");
        assertEquals(Math.min(50, corpus.count()), corpus.sample(99).size(), "the limit is clamped to at most 50");
    }

    @Test @Order(11)
    void radarShowsTheNamesOfEveryAccountInTheGroup() throws Exception {
        List<ProposalView> rows = proposals.radar(null);
        assertTrue(rows.size() >= 1);
        ProposalView g01 = rows.stream().filter(p -> p.getGroupCode().equals("G01")).findFirst().orElseThrow();
        assertTrue(g01.getMemberNames().size() >= 1, "the seeded account name is listed");
        assertTrue(g01.getMemberNames().stream().noneMatch(n -> n.contains("@")), "names only, no emails");
    }

    static String scalar(String sql) throws SQLException {
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getString(1);
        }
    }
}
