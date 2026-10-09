package np.edu.origin.service;

import np.edu.origin.dao.SyllabusDao;
import np.edu.origin.db.Database;
import np.edu.origin.model.Topic;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The syllabus layer: which topics exist, which ones past projects use, and how often each pair
 * of topics has been used together. The suggester reads these counts to say how untouched an
 * idea's topic mix is.
 */
public class SyllabusService {

    /**
     * @param usage past projects using each topic
     * @param pairs past projects using both topics of a pair, keyed by {@link #pairKey(int, int)}
     */
    public record Snapshot(List<Topic> topics, Map<Integer, Integer> usage, Map<Long, Integer> pairs, int projects) {

        public Topic topic(int id) {
            for (Topic t : topics) if (t.getId() == id) return t;
            return null;
        }

        public int pairUses(int a, int b) {
            return pairs.getOrDefault(pairKey(a, b), 0);
        }
    }

    private final SyllabusDao dao;
    private volatile TopicTagger tagger;
    private volatile Map<Integer, Set<Integer>> cachedProjectTopics;
    private volatile Snapshot cachedSnapshot;

    public SyllabusService(SyllabusDao dao) {
        this.dao = dao;
    }

    public List<Topic> topics() throws SQLException {
        return dao.topics();
    }

    /** problem id -> syllabus topic ids chosen for it. */
    public Map<Integer, Set<Integer>> problemTopics() throws SQLException {
        return dao.problemTopics();
    }

    private TopicTagger tagger() throws SQLException {
        TopicTagger t = tagger;
        if (t == null) {
            t = new TopicTagger(dao.topics());
            tagger = t;
        }
        return t;
    }

    /** Topic ids mentioned in the texts. */
    public Set<Integer> detect(String... texts) throws SQLException {
        return tagger().detect(texts);
    }

    /** Links every past project that has no topics yet. Returns how many projects were looked at. */
    public synchronized int linkNewProjects() throws SQLException {
        List<SyllabusDao.ProjectText> todo = dao.unlinkedProjects();
        if (todo.isEmpty()) return 0;
        try (Connection con = Database.connect()) {
            con.setAutoCommit(false);
            try {
                for (SyllabusDao.ProjectText p : todo) {
                    dao.linkProject(con, p.id(), detect(p.title(), p.abstractText(), p.tech() == null ? "" : p.tech()));
                }
                con.commit();
                cachedProjectTopics = null;
                cachedSnapshot = null;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
        return todo.size();
    }

    /** project id -> topic ids, cached until the next time projects are linked. */
    public Map<Integer, Set<Integer>> projectTopics() throws SQLException {
        Map<Integer, Set<Integer>> m = cachedProjectTopics;
        if (m == null) {
            m = dao.projectTopics();
            cachedProjectTopics = m;
        }
        return m;
    }

    /** Counts topic and pair usage over all linked past projects (cached like {@link #projectTopics()}). */
    public Snapshot snapshot() throws SQLException {
        Snapshot cached = cachedSnapshot;
        if (cached == null) {
            cached = buildSnapshot();
            cachedSnapshot = cached;
        }
        return cached;
    }

    private Snapshot buildSnapshot() throws SQLException {
        Map<Integer, Set<Integer>> byProject = projectTopics();
        Map<Integer, Integer> usage = new HashMap<>();
        Map<Long, Integer> pairs = new HashMap<>();
        for (Set<Integer> topicIds : byProject.values()) {
            Integer[] ids = topicIds.toArray(new Integer[0]);
            for (int i = 0; i < ids.length; i++) {
                usage.merge(ids[i], 1, Integer::sum);
                for (int j = i + 1; j < ids.length; j++) pairs.merge(pairKey(ids[i], ids[j]), 1, Integer::sum);
            }
        }
        return new Snapshot(dao.topics(), usage, pairs, dao.projectCount());
    }

    /** The same key for (a, b) and (b, a). */
    public static long pairKey(int a, int b) {
        return ((long) Math.min(a, b) << 32) | Math.max(a, b);
    }
}
