package np.edu.origin.service;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import np.edu.origin.dao.AdminDao;
import np.edu.origin.dao.CheckDao;
import np.edu.origin.dao.CorpusDao;
import np.edu.origin.dao.LookupDao;
import np.edu.origin.dao.ProposalDao;
import np.edu.origin.db.Database;
import np.edu.origin.decision.Answers;
import np.edu.origin.decision.DecisionClient;
import np.edu.origin.decision.EngineException;
import np.edu.origin.decision.Question;
import np.edu.origin.engine.Band;
import np.edu.origin.engine.Breakdown;
import np.edu.origin.engine.Candidate;
import np.edu.origin.engine.ClaimDetector;
import np.edu.origin.engine.Coverage;
import np.edu.origin.engine.MatchIndex;
import np.edu.origin.engine.Story;
import np.edu.origin.engine.Vocabulary;
import np.edu.origin.model.CheckView;
import np.edu.origin.model.IdeaInput;
import np.edu.origin.model.Lookup;
import np.edu.origin.model.MatchView;
import np.edu.origin.model.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executor;

/**
 * The originality check.
 *
 * Step 1 runs while the student waits: explainable rule scores against past projects and
 * this semester's locked class ideas, saved in one transaction.
 * Step 2 runs on a background thread: the decision engine judges whether the closest
 * matches are the same project in different words, and the verdict is refined.
 */
public class CheckService {

    static final int HISTORY_LIMIT = 5;
    static final int CLASS_LIMIT = 3;
    private static final int CONCEPT_HISTORY = HISTORY_LIMIT;   // every match on the page gets a meaning score
    private static final int CONCEPT_CLASS = CLASS_LIMIT;

    private final LookupDao lookups;
    private final CorpusDao corpus;
    private final ProposalDao proposals;
    private final CheckDao checks;
    private final AdminDao admin;
    private final DecisionClient decisions;
    private final Executor background;
    private final ClaimDetector claims = new ClaimDetector();

    public CheckService(LookupDao lookups, CorpusDao corpus, ProposalDao proposals, CheckDao checks, AdminDao admin,
                        DecisionClient decisions, Executor background) {
        this.lookups = lookups;
        this.corpus = corpus;
        this.proposals = proposals;
        this.checks = checks;
        this.admin = admin;
        this.decisions = decisions;
        this.background = background;
    }

    /** Validates, scores and stores an idea. Returns the new check id. */
    public int check(User user, IdeaInput in) throws ValidationException, SQLException {
        Vocabulary vocab = lookups.vocabulary();
        Set<String> tags = vocab.canonicalTags(in.getTags());
        validate(in, tags);

        Candidate idea = new Candidate(0, in.getTitle(), in.getTypeId(), tags,
                vocab.keywords(in.getTitle(), in.getAbstractText()));
        String fingerprint = fingerprint(idea);
        MatchIndex index = corpus.index(vocab);
        List<Breakdown> history = index.rank(idea, HISTORY_LIMIT);
        List<Breakdown> classPool = index.engine().rank(idea, proposals.activeCandidates(vocab, user.getGroupCode()), CLASS_LIMIT)
                .stream().filter(b -> b.ruleScore() > 0).toList();
        boolean claimFlag = !claims.unsupportedClaims(in.getTitle(), in.getTags(), in.getAbstractText()).isEmpty();
        int corpusSize = corpus.count();
        String engineStatus = decisions.isEnabled() ? "PENDING" : "OFF";

        int checkId;
        try (Connection con = Database.connect()) {
            con.setAutoCommit(false);
            try {
                // A supervisor's earlier correction for this exact idea wins over any computed score.
                Map<Integer, Double> remembered = new HashMap<>();
                for (Breakdown b : history) {
                    Boolean same = admin.findOverride(con, fingerprint, b.other().id());
                    if (same != null) remembered.put(b.other().id(), same ? 1.0 : 0.0);
                }
                double best = 0;
                for (Breakdown b : history) best = Math.max(best, Breakdown.blend(b.ruleScore(), remembered.get(b.other().id())));
                for (Breakdown b : classPool) best = Math.max(best, b.ruleScore());
                String verdict = Band.of(best, corpusSize).name();

                Map<String, Integer> tagIds = lookups.ensureTags(con, tags);
                checkId = checks.insert(con, user.getId(), lookups.activeSemesterId(), in, fingerprint, best, verdict,
                        claimFlag, engineStatus, tagIds);
                checks.insertMatches(con, checkId, "HISTORY", history, remembered);
                checks.insertMatches(con, checkId, "CLASS", classPool, Map.of());
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
        if (decisions.isEnabled()) {
            int id = checkId;
            background.execute(() -> refineQuietly(id));
        }
        return checkId;
    }

    private void validate(IdeaInput in, Set<String> tags) throws ValidationException, SQLException {
        Map<String, String> errors = new LinkedHashMap<>();
        if (in.getTitle().length() < 5 || in.getTitle().length() > 160) {
            errors.put("title", "Give the idea a title between 5 and 160 characters.");
        }
        if (in.getAbstractText().length() < 40) {
            errors.put("abstractText", "Describe what it does in at least two sentences (40 characters or more).");
        } else if (in.getAbstractText().length() > 2000) {
            errors.put("abstractText", "Keep the description under 2000 characters.");
        }
        if (in.getProblem().length() > 400) errors.put("problem", "Keep the problem under 400 characters.");
        if (in.getTargetSemester() < 1 || in.getTargetSemester() > 8) {
            errors.put("targetSemester", "Pick the semester you are building this for, from 1 to 8.");
        }
        if (tags.size() < 2) errors.put("tags", "Add at least two tags that describe what the project does.");
        else if (tags.size() > 10) errors.put("tags", "Use ten tags or fewer. Pick the ones about function.");
        if (lookups.domains().stream().map(Lookup::getId).noneMatch(id -> id == in.getDomainId())) {
            errors.put("domainId", "Pick a domain.");
        }
        if (lookups.types().stream().map(Lookup::getId).noneMatch(id -> id == in.getTypeId())) {
            errors.put("typeId", "Pick a project type.");
        }
        if (!errors.isEmpty()) throw new ValidationException(errors);
    }

    /** Same canonical tags and keywords give the same fingerprint, however the idea is worded or ordered. */
    static String fingerprint(Candidate idea) {
        String text = String.join(",", new TreeSet<>(idea.tags())) + "|" + String.join(",", new TreeSet<>(idea.keywords()));
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Loads a check for the result page. Students only see their own; supervisors see all. */
    public CheckView view(int checkId, User viewer) throws NotFoundException, SQLException {
        CheckView v = checks.find(checkId);
        if (v == null || (!viewer.isSupervisor() && v.getUserId() != viewer.getId())) {
            throw new NotFoundException("Check " + checkId + " not found");
        }
        v.getClaimReasons().addAll(claims.unsupportedClaims(v.getTitle(), String.join(",", v.getTags()), v.getAbstractText()));
        if (v.isClaimFlag() && v.getClaimReasons().isEmpty()) {
            v.getClaimReasons().add("The description names advanced features but does not explain how they will be built.");
        }
        return v;
    }

    /** Background step. Failures are recorded on the check and retried by the maintenance job. */
    public void refineQuietly(int checkId) {
        try {
            refine(checkId);
        } catch (EngineException | SQLException e) {
            System.err.println("[origin] refine of check " + checkId + " failed: " + e.getMessage());
            try {
                checks.setEngineStatus(checkId, "FAILED");
            } catch (SQLException ignored) {
                // the retry job will pick it up again because it is still not DONE
            }
        }
    }

    void refine(int checkId) throws EngineException, SQLException {
        CheckView v = checks.find(checkId);
        if (v == null || "DONE".equals(v.getEngineStatus())) return;

        List<MatchView> targets = new ArrayList<>();
        v.getHistory().stream().filter(m -> !m.isConceptKnown()).limit(CONCEPT_HISTORY).forEach(targets::add);
        v.getClassMatches().stream().filter(m -> !m.isConceptKnown()).limit(CONCEPT_CLASS).forEach(targets::add);

        JsonArrayBuilder candidates = Json.createArrayBuilder();
        Map<String, Question> questions = new LinkedHashMap<>();
        for (int i = 0; i < targets.size(); i++) {
            MatchView m = targets.get(i);
            candidates.add(Json.createObjectBuilder()
                    .add("title", m.getTitle())
                    .add("description", m.getAbstractText())
                    .add("domain", m.getDomainName())
                    .add("type", m.getTypeName())
                    .add("tags_shared_with_idea", String.join(", ", m.getSharedTags())));
            questions.put("same_" + i, Question.yesNo(
                    "Is `idea` essentially the same project as `candidates[" + i + "]`? Treat them as the same when both "
                  + "solve the same problem for the same users with the same core function, even if the title, wording "
                  + "or technology is different."));
        }
        // One narrow question, phrased so "yes" means a problem. Tested live: 0.03 for a sensor idea that names
        // its device, 0.85 for an "AI" idea that names nothing.
        questions.put("claims_unsupported", Question.yesNo(
                "Does `idea` claim an advanced technique (such as AI, machine learning, prediction, blockchain) that "
              + "its description gives no concrete basis for, such as the data, device or method used?"));

        JsonObject state = Json.createObjectBuilder()
                .add("idea", Json.createObjectBuilder()
                        .add("title", v.getTitle())
                        .add("description", v.getAbstractText())
                        .add("problem", v.getProblem() == null ? "" : v.getProblem())
                        .add("domain", v.getDomainName())
                        .add("type", v.getTypeName())
                        .add("tags", String.join(", ", v.getTags())))
                .add("candidates", candidates)
                .build();

        Answers answers = decisions.ask(state, questions);

        Map<Integer, Double> finals = new HashMap<>();
        for (MatchView m : v.getHistory()) finals.put(m.getId(), m.getFinalScore());
        for (MatchView m : v.getClassMatches()) finals.put(m.getId(), m.getFinalScore());

        try (Connection con = Database.connect()) {
            con.setAutoCommit(false);
            try {
                for (int i = 0; i < targets.size(); i++) {
                    MatchView m = targets.get(i);
                    double concept = answers.yes("same_" + i);
                    double blended = Breakdown.blend(m.getRuleScore(), concept);
                    checks.updateMatchConcept(con, m.getId(), concept, blended);
                    finals.put(m.getId(), blended);
                }
                double best = finals.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
                boolean claimFlag = v.isClaimFlag() || answers.yes("claims_unsupported") > 0.6;
                checks.updateOutcome(con, checkId, best, Band.of(best, corpus.count()).name(), claimFlag, "DONE");
                con.commit();
            } catch (SQLException | EngineException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /**
     * Supervisor correction on one past-project match. It is stored against the idea's fingerprint,
     * so the same idea checked again later gets the corrected verdict straight away.
     */
    public void override(User supervisor, int checkId, int matchId, boolean sameProject)
            throws NotFoundException, SQLException {
        CheckView v = view(checkId, supervisor);
        MatchView match = v.getHistory().stream().filter(m -> m.getId() == matchId).findFirst()
                .orElseThrow(() -> new NotFoundException("Match " + matchId + " not found"));
        double concept = sameProject ? 1.0 : 0.0;
        try (Connection con = Database.connect()) {
            con.setAutoCommit(false);
            try {
                admin.saveOverride(con, v.getFingerprint(), match.getTargetId(), sameProject, supervisor.getId());
                double blended = Breakdown.blend(match.getRuleScore(), concept);
                checks.updateMatchConcept(con, matchId, concept, blended);
                double best = blended;
                for (MatchView m : v.getHistory()) if (m.getId() != matchId) best = Math.max(best, m.getFinalScore());
                for (MatchView m : v.getClassMatches()) best = Math.max(best, m.getFinalScore());
                checks.updateOutcome(con, checkId, best, Band.of(best, corpus.count()).name(), v.isClaimFlag(),
                        v.getEngineStatus());
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /** The real numbers behind the evaluation screen: what Origin looked at for this idea. */
    public record Evaluation(int terms, int projects, int candidates, int classIdeas, int coveragePercent,
                             int coverageProjects) {
        public int getTerms()            { return terms; }
        public int getProjects()         { return projects; }
        public int getCandidates()       { return candidates; }
        public int getClassIdeas()       { return classIdeas; }
        public int getCoveragePercent()  { return coveragePercent; }
        public int getCoverageProjects() { return coverageProjects; }
    }

    /** Everything the result page adds on top of the stored check: stories, coverage and the fix sentence. */
    public record Extras(Map<Integer, Story> stories, Coverage coverage, String fix) {
        public Map<Integer, Story> getStories() { return stories; }
        public Coverage getCoverage()           { return coverage; }
        public String getFix()                  { return fix; }
    }

    private Candidate ideaOf(CheckView v, Vocabulary vocab) {
        return new Candidate(0, v.getTitle(), v.getTypeId(), new LinkedHashSet<>(v.getTags()),
                vocab.keywords(v.getTitle(), v.getAbstractText()));
    }

    public Evaluation evaluate(CheckView v, User viewer) throws SQLException {
        Vocabulary vocab = lookups.vocabulary();
        Candidate idea = ideaOf(v, vocab);
        MatchIndex index = corpus.index(vocab);
        List<Breakdown> top = index.rank(idea, 3);
        Coverage cov = index.engine().coverage(idea, top, 3);
        int classIdeas = proposals.activeCandidates(vocab, viewer.getGroupCode()).size();
        return new Evaluation(idea.tags().size() + idea.keywords().size(), index.size(), index.candidateCount(idea),
                classIdeas, cov.percent(), cov.projects());
    }

    public Extras extras(CheckView v) throws SQLException {
        Vocabulary vocab = lookups.vocabulary();
        Candidate idea = ideaOf(v, vocab);
        MatchIndex index = corpus.index(vocab);
        Map<Integer, Story> stories = new HashMap<>();
        for (MatchView m : v.getHistory()) {
            Candidate other = index.candidate(m.getTargetId());
            if (other == null) continue;
            stories.put(m.getId(), index.engine().story(idea, other));
        }
        // The fix sentence speaks about the strongest past match, so it always matches the top row of the table.
        String fix = "";
        MatchView top = null;
        for (MatchView m : v.getHistory()) {
            if (m.getPercent() > 0 && stories.containsKey(m.getId())
                    && (top == null || m.getFinalScore() > top.getFinalScore())) top = m;
        }
        if (top != null) fix = stories.get(top.getId()).fix(top.getTitle(), top.getPercent());
        Coverage cov = index.engine().coverage(idea, index.rank(idea, 3), 3);
        return new Extras(stories, cov, fix);
    }

    public boolean engineEnabled() {
        return decisions.isEnabled();
    }
}
