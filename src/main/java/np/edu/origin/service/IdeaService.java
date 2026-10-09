package np.edu.origin.service;

// Jev (decision engine) imports. Switched off for now, see the commented block in refresh().
// import jakarta.json.Json;
// import jakarta.json.JsonArrayBuilder;
// import jakarta.json.JsonObject;
// import np.edu.origin.decision.Answers;
// import np.edu.origin.decision.EngineException;
// import np.edu.origin.decision.Question;
import np.edu.origin.dao.CorpusDao;
import np.edu.origin.dao.GapDao;
import np.edu.origin.dao.IdeaDao;
import np.edu.origin.dao.LookupDao;
import np.edu.origin.dao.ProposalDao;
import np.edu.origin.dao.SyllabusDao;
import np.edu.origin.decision.DecisionClient;
import np.edu.origin.engine.Band;
import np.edu.origin.engine.Breakdown;
import np.edu.origin.engine.Candidate;
import np.edu.origin.engine.MatchIndex;
import np.edu.origin.engine.SimilarityEngine;
import np.edu.origin.engine.Vocabulary;
import np.edu.origin.model.GapCell;
import np.edu.origin.model.Lookup;
import np.edu.origin.model.Problem;
import np.edu.origin.model.Suggestion;
import np.edu.origin.model.Topic;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Builds the project suggestions shown on the Ideas page.
 *
 * Every problem in the problem bank is paired with its suitable project types and with 2 to 4
 * topics of the BIT syllabus. Each pairing is scored on four things:
 *   untouched   1 / (1 + past projects that used the idea's busiest topic pair together)
 *   coverage    distinct syllabus units the idea touches, out of 4, capped at 1
 *   feasibility how much a 3-person team can build in one semester
 *   impact      whether it helps a clear group of people
 * strength = 0.40 untouched + 0.30 coverage + 0.20 feasibility + 0.10 impact
 *
 * An idea whose topics are all everyday CRUD plumbing (JDBC, Servlet and JSP, ER design) is
 * flagged "plain CRUD" and ranked after every other idea. An idea that is already built (60% or
 * more similar to a past project) is retired, and one a group has locked this semester is marked
 * taken.
 */
public class IdeaService {

    /** A locked idea at least this close to a suggestion means another group has taken it. */
    static final double TAKEN_FROM = 0.45;

    static final double W_UNTOUCHED = 0.40;
    static final double W_COVERAGE = 0.30;
    static final double W_FEASIBILITY = 0.20;
    static final double W_IMPACT = 0.10;
    /** A project that touches this many syllabus units earns full coverage credit. */
    static final int FULL_COVERAGE_UNITS = 4;
    /** Strength given to a plain CRUD idea at most, so it ranks after real ideas. */
    static final double CRUD_STRENGTH_CAP = 0.25;

    private final LookupDao lookups;
    private final CorpusDao corpus;
    private final ProposalDao proposals;
    private final IdeaDao ideas;
    private final GapDao gaps;
    private final SyllabusService syllabus;
    private final SyllabusDao syllabusDao;
    // Kept for the Jev block below; unused while the decision engine is off.
    private final DecisionClient decisions;
    private final Executor background;

    public IdeaService(LookupDao lookups, CorpusDao corpus, ProposalDao proposals, IdeaDao ideas, GapDao gaps,
                       SyllabusService syllabus, SyllabusDao syllabusDao, DecisionClient decisions, Executor background) {
        this.lookups = lookups;
        this.corpus = corpus;
        this.proposals = proposals;
        this.ideas = ideas;
        this.gaps = gaps;
        this.syllabus = syllabus;
        this.syllabusDao = syllabusDao;
        this.decisions = decisions;
        this.background = background;
    }

    /** What an idea's topics say about it, computed from the syllabus counts. */
    record Evidence(int pairUses, String pairLabel, int units, int courses, boolean crudOnly, String topicList) { }

    /** Recomputes every suggestion from the current corpus, class proposals, problem bank and syllabus counts. */
    public synchronized int refresh() throws SQLException {
        syllabus.linkNewProjects();
        Vocabulary vocab = lookups.vocabulary();
        MatchIndex index = corpus.index(vocab);
        SimilarityEngine engine = index.engine();
        List<Candidate> locked = proposals.activeCandidates(vocab, null);
        Map<Integer, String> groups = proposals.groupCodes();
        SyllabusService.Snapshot snap = syllabus.snapshot();
        Map<Integer, Set<Integer>> curated = syllabusDao.problemTopics();

        Map<String, Integer> cellCount = new HashMap<>();
        for (GapCell c : gaps.matrix()) cellCount.put(c.getDomainId() + ":" + c.getTypeId(), c.getProjectCount());
        Map<Integer, Lookup> types = new HashMap<>();
        for (Lookup t : lookups.types()) types.put(t.getId(), t);
        // Map<Integer, String> domains = new HashMap<>();                      // Jev block
        // for (Lookup d : lookups.domains()) domains.put(d.getId(), d.getName());

        LocalDateTime lastArchive = lookups.lastArchiveTime();
        int written = 0;
        for (Problem p : ideas.problems()) {
            // A student's problem becomes a public suggestion only after their semester is archived,
            // so nobody can pick up a classmate's idea while it is still being decided.
            if ("STUDENT".equals(p.source()) && (lastArchive == null || !p.createdAt().isBefore(lastArchive))) continue;

            Set<Integer> topicIds = curated.containsKey(p.id()) ? curated.get(p.id())
                                                                : syllabus.detect(p.solution(), p.statement());
            Evidence ev = evidence(topicIds, snap);

            List<Integer> typeIds = new ArrayList<>();
            typeIds.add(p.primaryTypeId());
            if (p.altTypeId() != null && !p.altTypeId().equals(p.primaryTypeId())) typeIds.add(p.altTypeId());

            for (int typeId : typeIds) {
                Candidate idea = new Candidate(0, p.solution(), typeId, p.tags(),
                        vocab.keywords(p.solution(), p.statement()));
                List<Breakdown> nearPast = index.rank(idea, 3);
                List<Breakdown> nearLocked = engine.rank(idea, locked, 1);

                Suggestion s = new Suggestion();
                s.setProblemId(p.id());
                s.setTypeId(typeId);
                s.setTitle(p.solution());
                s.setGapCount(cellCount.getOrDefault(p.domainId() + ":" + typeId, 0));

                double pastScore = nearPast.isEmpty() ? 0 : nearPast.get(0).ruleScore();
                double lockedScore = nearLocked.isEmpty() ? 0 : nearLocked.get(0).ruleScore();
                if (lockedScore > pastScore) {
                    s.setNearestScore(lockedScore);
                    s.setNearestTitle(nearLocked.get(0).other().title() + " (this semester)");
                } else {
                    s.setNearestScore(pastScore);
                    s.setNearestTitle(nearPast.isEmpty() ? null : nearPast.get(0).other().title());
                }

                int effort = Integer.parseInt(types.get(typeId).getCode());
                s.setNovelty(1 - s.getNearestScore());
                s.setFeasibility(feasibilityFromEffort(effort));
                s.setImpact("SEED".equals(p.source()) ? 0.70 : 0.60);
                s.setDifficulty(Math.max(1, Math.min(4, effort - 1)));
                s.setStrength(strength(ev, s.getFeasibility(), s.getImpact()));

                if (lockedScore >= TAKEN_FROM) {
                    s.setStatus("TAKEN");
                    s.setTakenByGroup(groups.get(nearLocked.get(0).other().id()));
                } else if (pastScore >= Band.DUPLICATE_FROM) {
                    s.setStatus("RETIRED");
                } else {
                    s.setStatus("OPEN");
                }
                ideas.upsert(s, false);
                syllabusDao.saveProof(p.id(), typeId, new SyllabusDao.Proof(ev.pairLabel(), ev.pairUses(),
                        snap.projects(), ev.units(), ev.courses(), ev.crudOnly(), ev.topicList()));
                written++;

                // ---- Jev second opinion: switched off. To use it again, restore the imports at the top, the
                // ---- scoreQuietly method below, the `domains` map above, and uncomment this block.
                // if (decisions.isEnabled()) {
                //     String domainName = domains.get(p.domainId());
                //     String typeName = types.get(typeId).getName();
                //     background.execute(() -> scoreQuietly(p, s, domainName, typeName, nearPast));
                // }
            }
        }
        return written;
    }

    /** Turns an idea's topic ids into the numbers shown as its proof. */
    static Evidence evidence(Set<Integer> topicIds, SyllabusService.Snapshot snap) {
        List<Topic> topics = new ArrayList<>();
        for (int id : topicIds) {
            Topic t = snap.topic(id);
            if (t != null) topics.add(t);
        }
        if (topics.isEmpty()) return new Evidence(0, null, 0, 0, true, "");

        int bestUses = -1;
        String bestLabel = null;
        if (topics.size() == 1) {
            Topic only = topics.get(0);
            bestUses = snap.usage().getOrDefault(only.getId(), 0);
            bestLabel = only.getName();
        }
        for (int i = 0; i < topics.size(); i++) {
            for (int j = i + 1; j < topics.size(); j++) {
                int uses = snap.pairUses(topics.get(i).getId(), topics.get(j).getId());
                if (uses > bestUses) {
                    bestUses = uses;
                    bestLabel = topics.get(i).getName() + " + " + topics.get(j).getName();
                }
            }
        }

        Set<String> units = new LinkedHashSet<>();
        Set<String> courses = new HashSet<>();
        boolean allCrud = true;
        List<String> names = new ArrayList<>();
        for (Topic t : topics) {
            names.add(t.getName());
            if (!t.isCrud()) {
                allCrud = false;
                units.add(t.getUnitKey());
                courses.add(t.getCourse());
            }
        }
        return new Evidence(bestUses, bestLabel, units.size(), courses.size(), allCrud, String.join(", ", names));
    }

    static double feasibilityFromEffort(int effort) {
        return Math.max(0.3, 1.0 - (effort - 1) * 0.15);
    }

    static double untouched(int pairUses) {
        return 1.0 / (1.0 + pairUses);
    }

    static double coverage(int units) {
        return Math.min(1.0, units / (double) FULL_COVERAGE_UNITS);
    }

    static double strength(Evidence ev, double feasibility, double impact) {
        double s = W_UNTOUCHED * untouched(ev.pairUses()) + W_COVERAGE * coverage(ev.units())
                 + W_FEASIBILITY * feasibility + W_IMPACT * impact;
        return ev.crudOnly() ? Math.min(s, CRUD_STRENGTH_CAP) : s;
    }

    // ---- Jev second opinion: switched off. The method is kept as written so it can be turned back on.
    //
    // private void scoreQuietly(Problem p, Suggestion s, String domainName, String typeName, List<Breakdown> nearPast) {
    //     try {
    //         JsonArrayBuilder closest = Json.createArrayBuilder();
    //         for (Breakdown b : nearPast) closest.add(b.other().title());
    //         JsonObject state = Json.createObjectBuilder()
    //                 .add("idea", Json.createObjectBuilder()
    //                         .add("title", p.solution())
    //                         .add("problem", p.statement())
    //                         .add("who_is_affected", p.affected())
    //                         .add("domain", domainName)
    //                         .add("project_type", typeName))
    //                 .add("closest_existing_projects", closest)
    //                 .add("team", "Three second-year BIT students in Nepal, one semester of about four months, "
    //                         + "building with Java and MySQL alongside five other courses.")
    //                 .build();
    //
    //         Map<String, Question> q = new LinkedHashMap<>();
    //         q.put("feasible", Question.yesNo("Can the `team` build a working, demonstrable version of `idea` within one semester?"));
    //         q.put("impact", Question.yesNo("Does `idea` solve a real, specific problem for an identifiable group of people in Nepal?"));
    //         q.put("novelty", Question.scale("How different is `idea` from `closest_existing_projects`?",
    //                 "Practically the same project", "The same idea with small changes",
    //                 "A clearly different angle on a known topic", "A new problem or new users", "Nothing like it exists"));
    //         q.put("difficulty", Question.scale("How hard is `idea` for the `team`?",
    //                 "Easy", "Moderate", "Challenging", "Hard"));
    //
    //         Answers a = decisions.ask(state, q);
    //         s.setFeasibility(a.yes("feasible"));
    //         s.setImpact(a.yes("impact"));
    //         s.setNovelty(Math.min(1.0, a.level("novelty") / 4.0));
    //         s.setDifficulty((int) Math.round(a.level("difficulty")) + 1);
    //         s.setStrength(strength(evidence, s.getFeasibility(), s.getImpact()));
    //         ideas.upsert(s, true);
    //     } catch (EngineException | SQLException e) {
    //         // The rule-based values stay in place; the next refresh tries again.
    //         System.err.println("[origin] idea scoring skipped for problem " + p.id() + ": " + e.getMessage());
    //     }
    // }

    /**
     * Suggestions for display, strongest first. Without a type filter each problem appears once,
     * with whichever of its project types scored higher.
     */
    public List<Suggestion> list(Integer domainId, Integer typeId, Integer maxDifficulty, boolean includeTaken)
            throws SQLException {
        return list(domainId, typeId, maxDifficulty, null, includeTaken);
    }

    /** As above, optionally keeping only ideas that use one syllabus topic. */
    public List<Suggestion> list(Integer domainId, Integer typeId, Integer maxDifficulty, Integer topicId,
                                 boolean includeTaken) throws SQLException {
        List<Suggestion> all = ideas.list(domainId, typeId, maxDifficulty, topicId, includeTaken, 500);
        if (typeId != null) return all;
        Set<Integer> seen = new HashSet<>();
        List<Suggestion> best = new ArrayList<>();
        for (Suggestion s : all) {
            if (seen.add(s.getProblemId())) best.add(s);
        }
        return best;
    }

    public Suggestion find(int id) throws SQLException {
        return ideas.find(id);
    }
}
