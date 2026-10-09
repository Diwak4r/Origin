package np.edu.origin.dao;

import np.edu.origin.db.Database;
import np.edu.origin.model.Topic;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Syllabus topics and their links to past projects, problems and suggestions. */
public class SyllabusDao {

    /** A past project that has no topic links yet. */
    public record ProjectText(int id, String title, String abstractText, String tech) { }

    /** The numbers behind one suggestion. */
    public record Proof(String pairLabel, int pairUses, int totalProjects, int units, int courses,
                        boolean crudOnly, String topicList) { }

    public List<Topic> topics() throws SQLException {
        List<Topic> out = new ArrayList<>();
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT id, course, unit_no, name, crud, keywords FROM syllabus_topics ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new Topic(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getBoolean(5),
                        Arrays.asList(rs.getString(6).split(","))));
            }
        }
        return out;
    }

    /** Every past project, including ones whose text mentions no syllabus topic. */
    public int projectCount() throws SQLException {
        return scalar("SELECT COUNT(*) FROM corpus_projects");
    }

    public int topicCount() throws SQLException {
        return scalar("SELECT COUNT(*) FROM syllabus_topics");
    }

    /** Seeder only. Row: course, unit, name, crud (0 or 1), keywords. */
    public void insertTopic(Connection con, String course, int unit, String name, boolean crud, String keywords)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT IGNORE INTO syllabus_topics (course, unit_no, name, crud, keywords) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, course);
            ps.setInt(2, unit);
            ps.setString(3, name);
            ps.setBoolean(4, crud);
            ps.setString(5, keywords);
            ps.executeUpdate();
        }
    }

    /** project id -> topic ids, for every linked past project. */
    public Map<Integer, Set<Integer>> projectTopics() throws SQLException {
        return pairs("SELECT project_id, topic_id FROM corpus_project_topics");
    }

    /** problem id -> topic ids. */
    public Map<Integer, Set<Integer>> problemTopics() throws SQLException {
        return pairs("SELECT problem_id, topic_id FROM problem_topics");
    }

    private Map<Integer, Set<Integer>> pairs(String sql) throws SQLException {
        Map<Integer, Set<Integer>> out = new HashMap<>();
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.computeIfAbsent(rs.getInt(1), k -> new HashSet<>()).add(rs.getInt(2));
        }
        return out;
    }

    public Map<Integer, String> twists() throws SQLException {
        Map<Integer, String> out = new HashMap<>();
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement("SELECT problem_id, twist FROM problem_twist");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.put(rs.getInt(1), rs.getString(2));
        }
        return out;
    }

    /** Past projects with no topic link at all (new, imported or archived since the last pass). */
    public List<ProjectText> unlinkedProjects() throws SQLException {
        List<ProjectText> out = new ArrayList<>();
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT cp.id, cp.title, cp.abstract, cp.tech FROM corpus_projects cp "
                   + "WHERE NOT EXISTS (SELECT 1 FROM corpus_project_topics t WHERE t.project_id = cp.id)");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(new ProjectText(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)));
        }
        return out;
    }

    public void linkProject(Connection con, int projectId, Collection<Integer> topicIds) throws SQLException {
        link(con, "INSERT IGNORE INTO corpus_project_topics (project_id, topic_id) VALUES (?, ?)", projectId, topicIds);
    }

    public Integer problemIdByStatement(String statement) throws SQLException {
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement("SELECT id FROM problem_bank WHERE statement = ?")) {
            ps.setString(1, statement);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    public boolean problemHasTopics(int problemId) throws SQLException {
        return scalar("SELECT COUNT(*) FROM problem_topics WHERE problem_id = " + problemId) > 0;
    }

    public void linkProblem(Connection con, int problemId, Collection<Integer> topicIds, String twist)
            throws SQLException {
        link(con, "INSERT IGNORE INTO problem_topics (problem_id, topic_id) VALUES (?, ?)", problemId, topicIds);
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO problem_twist (problem_id, twist) VALUES (?, ?) ON DUPLICATE KEY UPDATE twist = VALUES(twist)")) {
            ps.setInt(1, problemId);
            ps.setString(2, twist);
            ps.executeUpdate();
        }
    }

    private static void link(Connection con, String sql, int id, Collection<Integer> topicIds) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int topicId : topicIds) {
                ps.setInt(1, id);
                ps.setInt(2, topicId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Stores the proof of the suggestion identified by problem and type. The suggestion row must exist. */
    public void saveProof(int problemId, int typeId, Proof p) throws SQLException {
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO suggestion_proof (suggestion_id, pair_label, pair_uses, total_projects, units, courses, "
                   + "  crud_only, topic_list) "
                   + "SELECT id, ?, ?, ?, ?, ?, ?, ? FROM idea_suggestions WHERE problem_id = ? AND type_id = ? "
                   + "ON DUPLICATE KEY UPDATE pair_label = VALUES(pair_label), pair_uses = VALUES(pair_uses), "
                   + "  total_projects = VALUES(total_projects), units = VALUES(units), courses = VALUES(courses), "
                   + "  crud_only = VALUES(crud_only), topic_list = VALUES(topic_list)")) {
            ps.setString(1, p.pairLabel());
            ps.setInt(2, p.pairUses());
            ps.setInt(3, p.totalProjects());
            ps.setInt(4, p.units());
            ps.setInt(5, p.courses());
            ps.setBoolean(6, p.crudOnly());
            ps.setString(7, p.topicList());
            ps.setInt(8, problemId);
            ps.setInt(9, typeId);
            ps.executeUpdate();
        }
    }

    private static int scalar(String sql) throws SQLException {
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
