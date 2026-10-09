package np.edu.origin.dao;

import np.edu.origin.db.Database;
import np.edu.origin.model.Problem;
import np.edu.origin.model.Suggestion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The problem bank and the idea suggestions generated from it. */
public class IdeaDao {

    public List<Problem> problems() throws SQLException {
        List<Problem> out = new ArrayList<>();
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT pb.*, GROUP_CONCAT(t.name ORDER BY t.name SEPARATOR ',') AS tag_list "
                   + "FROM problem_bank pb LEFT JOIN problem_tags pt ON pt.problem_id = pb.id "
                   + "LEFT JOIN tags t ON t.id = pt.tag_id GROUP BY pb.id ORDER BY pb.id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int alt = rs.getInt("alt_type_id");
                Integer altType = rs.wasNull() ? null : alt;
                out.add(new Problem(rs.getInt("id"), rs.getInt("domain_id"), rs.getString("statement"),
                        rs.getString("affected"), rs.getString("solution_phrase"), rs.getInt("primary_type_id"),
                        altType, rs.getString("source"), CorpusDao.splitTags(rs.getString("tag_list")),
                        rs.getTimestamp("created_at").toLocalDateTime()));
            }
        }
        return out;
    }

    /** Seeder only: inserts one problem with its tags. */
    public void insertProblem(Connection con, int domainId, String statement, String affected, String solution,
                              int primaryType, Integer altType, Map<String, Integer> tagIds) throws SQLException {
        int id;
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO problem_bank (domain_id, statement, affected, solution_phrase, primary_type_id, alt_type_id) "
              + "VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, domainId);
            ps.setString(2, statement);
            ps.setString(3, affected);
            ps.setString(4, solution);
            ps.setInt(5, primaryType);
            if (altType == null) ps.setNull(6, Types.INTEGER); else ps.setInt(6, altType);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                id = keys.getInt(1);
            }
        }
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO problem_tags (problem_id, tag_id) VALUES (?, ?)")) {
            for (int tagId : tagIds.values()) {
                ps.setInt(1, id);
                ps.setInt(2, tagId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Inserts or refreshes one suggestion. A rules write replaces every value, so a refresh always
     * reflects the current corpus; {@code engine} writes are the decision engine's (switched off for now).
     */
    public void upsert(Suggestion s, boolean engine) throws SQLException {
        String sql =
                "INSERT INTO idea_suggestions (problem_id, type_id, title, gap_count, nearest_score, nearest_title, "
              + "  novelty, feasibility, impact, difficulty, strength, status, scored_by, taken_by_group, refreshed_at) "
              + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW()) "
              + "ON DUPLICATE KEY UPDATE title = VALUES(title), gap_count = VALUES(gap_count), "
              + "  nearest_score = VALUES(nearest_score), nearest_title = VALUES(nearest_title), "
              + "  status = VALUES(status), taken_by_group = VALUES(taken_by_group), refreshed_at = NOW(), "
              + (engine
                 ? "  novelty = VALUES(novelty), feasibility = VALUES(feasibility), impact = VALUES(impact), "
                 + "  difficulty = VALUES(difficulty), strength = VALUES(strength), scored_by = 'ENGINE'"
                 : "  novelty = VALUES(novelty), feasibility = VALUES(feasibility), impact = VALUES(impact), "
                 + "  difficulty = VALUES(difficulty), strength = VALUES(strength), scored_by = 'RULES'");
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, s.getProblemId());
            ps.setInt(2, s.getTypeId());
            ps.setString(3, s.getTitle());
            ps.setInt(4, s.getGapCount());
            ps.setDouble(5, s.getNearestScore());
            ps.setString(6, s.getNearestTitle());
            ps.setDouble(7, s.getNovelty());
            ps.setDouble(8, s.getFeasibility());
            ps.setDouble(9, s.getImpact());
            ps.setInt(10, s.getDifficulty());
            ps.setDouble(11, s.getStrength());
            ps.setString(12, s.getStatus());
            ps.setString(13, engine ? "ENGINE" : "RULES");
            ps.setString(14, s.getTakenByGroup());
            ps.executeUpdate();
        }
    }

    private static final String SELECT_SUGGESTION =
            "SELECT sg.*, pb.statement, pb.affected, pb.source, pb.domain_id, d.name AS domain_name, t.name AS type_name, "
          + "       (SELECT GROUP_CONCAT(tg.name ORDER BY tg.name) FROM problem_tags pt JOIN tags tg ON tg.id = pt.tag_id "
          + "         WHERE pt.problem_id = pb.id) AS tag_list, "
          + "       sp.pair_label, sp.pair_uses, sp.total_projects, sp.units, sp.courses, sp.crud_only, sp.topic_list, "
          + "       tw.twist "
          + "FROM idea_suggestions sg "
          + "JOIN problem_bank pb ON pb.id = sg.problem_id "
          + "JOIN domains d ON d.id = pb.domain_id "
          + "JOIN project_types t ON t.id = sg.type_id "
          + "LEFT JOIN suggestion_proof sp ON sp.suggestion_id = sg.id "
          + "LEFT JOIN problem_twist tw ON tw.problem_id = pb.id ";

    /** Suggestions for the ideas page. Any filter may be null. */
    public List<Suggestion> list(Integer domainId, Integer typeId, Integer maxDifficulty, boolean includeTaken,
                                 int limit) throws SQLException {
        return list(domainId, typeId, maxDifficulty, null, includeTaken, limit);
    }

    /** As above, optionally keeping only problems that use one syllabus topic. Plain CRUD ideas sort last. */
    public List<Suggestion> list(Integer domainId, Integer typeId, Integer maxDifficulty, Integer topicId,
                                 boolean includeTaken, int limit) throws SQLException {
        String sql = SELECT_SUGGESTION
              + "WHERE (? IS NULL OR pb.domain_id = ?) AND (? IS NULL OR sg.type_id = ?) "
              + "  AND (? IS NULL OR sg.difficulty <= ?) AND (? OR sg.status = 'OPEN') "
              + "  AND (? IS NULL OR EXISTS (SELECT 1 FROM problem_topics ptop WHERE ptop.problem_id = pb.id AND ptop.topic_id = ?)) "
              + "ORDER BY sg.status = 'OPEN' DESC, COALESCE(sp.crud_only, 0) ASC, sg.strength DESC, COALESCE(sp.units, 0) DESC, sg.novelty DESC, sg.id LIMIT ?";
        List<Suggestion> out = new ArrayList<>();
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
            setNullable(ps, 1, domainId);
            setNullable(ps, 2, domainId);
            setNullable(ps, 3, typeId);
            setNullable(ps, 4, typeId);
            setNullable(ps, 5, maxDifficulty);
            setNullable(ps, 6, maxDifficulty);
            ps.setBoolean(7, includeTaken);
            setNullable(ps, 8, topicId);
            setNullable(ps, 9, topicId);
            ps.setInt(10, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    public Suggestion find(int id) throws SQLException {
        String sql = SELECT_SUGGESTION + "WHERE sg.id = ?";
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public int countOpen() throws SQLException {
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM idea_suggestions WHERE status = 'OPEN'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public int countProblems(String source) throws SQLException {
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM problem_bank WHERE source = ?")) {
            ps.setString(1, source);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static Suggestion map(ResultSet rs) throws SQLException {
        Suggestion s = new Suggestion();
        s.setId(rs.getInt("id"));
        s.setProblemId(rs.getInt("problem_id"));
        s.setTitle(rs.getString("title"));
        s.setStatement(rs.getString("statement"));
        s.setAffected(rs.getString("affected"));
        s.setSource(rs.getString("source"));
        s.setDomainId(rs.getInt("domain_id"));
        s.setDomainName(rs.getString("domain_name"));
        s.setTypeId(rs.getInt("type_id"));
        s.setTypeName(rs.getString("type_name"));
        s.setGapCount(rs.getInt("gap_count"));
        s.setNearestScore(rs.getDouble("nearest_score"));
        s.setNearestTitle(rs.getString("nearest_title"));
        s.setNovelty(rs.getDouble("novelty"));
        s.setFeasibility(rs.getDouble("feasibility"));
        s.setImpact(rs.getDouble("impact"));
        s.setDifficulty(rs.getInt("difficulty"));
        s.setStrength(rs.getDouble("strength"));
        s.setStatus(rs.getString("status"));
        s.setTakenByGroup(rs.getString("taken_by_group"));
        s.setTags(new ArrayList<>(CorpusDao.splitTags(rs.getString("tag_list"))));
        s.setTwist(rs.getString("twist"));
        s.setPairLabel(rs.getString("pair_label"));
        s.setPairUses(rs.getInt("pair_uses"));
        s.setTotalProjects(rs.getInt("total_projects"));
        s.setUnits(rs.getInt("units"));
        s.setCourses(rs.getInt("courses"));
        s.setCrudOnly(rs.getBoolean("crud_only"));
        String topicList = rs.getString("topic_list");
        s.setTopics(topicList == null || topicList.isBlank() ? List.of() : List.of(topicList.split(", ")));
        return s;
    }

    private static void setNullable(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) ps.setNull(index, Types.INTEGER); else ps.setInt(index, value);
    }
}
