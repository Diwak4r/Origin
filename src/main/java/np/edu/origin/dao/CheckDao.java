package np.edu.origin.dao;

import np.edu.origin.db.Database;
import np.edu.origin.engine.Breakdown;
import np.edu.origin.model.CheckView;
import np.edu.origin.model.IdeaInput;
import np.edu.origin.model.MatchView;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Idea checks, their tags and their scored matches. */
public class CheckDao {

    /** Saves the check row and its tags. Runs inside the caller's transaction. */
    public int insert(Connection con, int userId, int semesterId, IdeaInput in, String fingerprint,
                      double ruleScore, String verdict, boolean claimFlag, String engineStatus,
                      Map<String, Integer> tagIds) throws SQLException {
        int id;
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO idea_checks (user_id, semester_id, title, abstract, problem, domain_id, type_id, "
              + "target_semester, fingerprint, rule_score, final_score, verdict, claim_flag, engine_status) "
              + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setInt(2, semesterId);
            ps.setString(3, in.getTitle());
            ps.setString(4, in.getAbstractText());
            if (in.getProblem().isEmpty()) ps.setNull(5, Types.VARCHAR); else ps.setString(5, in.getProblem());
            ps.setInt(6, in.getDomainId());
            ps.setInt(7, in.getTypeId());
            ps.setInt(8, in.getTargetSemester());
            ps.setString(9, fingerprint);
            ps.setDouble(10, ruleScore);
            ps.setDouble(11, ruleScore);
            ps.setString(12, verdict);
            ps.setBoolean(13, claimFlag);
            ps.setString(14, engineStatus);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                id = keys.getInt(1);
            }
        }
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO idea_check_tags (check_id, tag_id) VALUES (?, ?)")) {
            for (int tagId : tagIds.values()) {
                ps.setInt(1, id);
                ps.setInt(2, tagId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        return id;
    }

    /** Saves the ranked matches for one pool. {@code concepts} may hold a remembered supervisor verdict per target. */
    public void insertMatches(Connection con, int checkId, String pool, List<Breakdown> ranked,
                              Map<Integer, Double> concepts) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO check_matches (check_id, pool, corpus_project_id, proposal_id, rank_no, tag_score, "
              + "keyword_score, type_score, rule_score, concept_score, final_score) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int rank = 1;
            for (Breakdown b : ranked) {
                int target = b.other().id();
                Double concept = concepts.get(target);
                ps.setInt(1, checkId);
                ps.setString(2, pool);
                if ("HISTORY".equals(pool)) {
                    ps.setInt(3, target);
                    ps.setNull(4, Types.INTEGER);
                } else {
                    ps.setNull(3, Types.INTEGER);
                    ps.setInt(4, target);
                }
                ps.setInt(5, rank++);
                ps.setDouble(6, b.tagScore());
                ps.setDouble(7, b.keywordScore());
                ps.setDouble(8, b.typeScore());
                ps.setDouble(9, b.ruleScore());
                if (concept == null) ps.setNull(10, Types.DECIMAL); else ps.setDouble(10, concept);
                ps.setDouble(11, Breakdown.blend(b.ruleScore(), concept));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public CheckView find(int checkId) throws SQLException {
        try (Connection con = Database.connect()) {
            CheckView v = null;
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT ic.*, d.name AS domain_name, t.name AS type_name, p.id AS proposal_id "
                  + "FROM idea_checks ic JOIN domains d ON d.id = ic.domain_id "
                  + "JOIN project_types t ON t.id = ic.type_id "
                  + "LEFT JOIN proposals p ON p.check_id = ic.id WHERE ic.id = ?")) {
                ps.setInt(1, checkId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) v = mapCheck(rs);
                }
            }
            if (v == null) return null;
            v.setTags(tagsOf(con, checkId));
            loadMatches(con, v);
            return v;
        }
    }

    private CheckView mapCheck(ResultSet rs) throws SQLException {
        CheckView v = new CheckView();
        v.setId(rs.getInt("id"));
        v.setUserId(rs.getInt("user_id"));
        v.setTitle(rs.getString("title"));
        v.setAbstractText(rs.getString("abstract"));
        v.setProblem(rs.getString("problem"));
        v.setDomainId(rs.getInt("domain_id"));
        v.setDomainName(rs.getString("domain_name"));
        v.setTypeId(rs.getInt("type_id"));
        v.setTypeName(rs.getString("type_name"));
        v.setTargetSemester(rs.getInt("target_semester"));
        v.setVerdict(rs.getString("verdict"));
        v.setRuleScore(rs.getDouble("rule_score"));
        v.setFinalScore(rs.getDouble("final_score"));
        v.setClaimFlag(rs.getBoolean("claim_flag"));
        v.setEngineStatus(rs.getString("engine_status"));
        v.setFingerprint(rs.getString("fingerprint"));
        v.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        int proposal = rs.getInt("proposal_id");
        v.setProposalId(rs.wasNull() ? null : proposal);
        return v;
    }

    List<String> tagsOf(Connection con, int checkId) throws SQLException {
        List<String> tags = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT t.name FROM idea_check_tags ict JOIN tags t ON t.id = ict.tag_id WHERE ict.check_id = ? ORDER BY t.name")) {
            ps.setInt(1, checkId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) tags.add(rs.getString(1));
            }
        }
        return tags;
    }

    private void loadMatches(Connection con, CheckView v) throws SQLException {
        String sql =
                "SELECT cm.*, COALESCE(cp.title, pic.title) AS m_title, COALESCE(cp.abstract, pic.abstract) AS m_abstract, "
              + "       cp.year AS m_year, pr.group_code AS m_group, COALESCE(d1.name, d2.name) AS m_domain, "
              + "       COALESCE(t1.name, t2.name) AS m_type, "
              + "       (SELECT GROUP_CONCAT(tg.name) FROM corpus_project_tags x JOIN tags tg ON tg.id = x.tag_id "
              + "         WHERE x.project_id = cp.id) AS cp_tags, "
              + "       (SELECT GROUP_CONCAT(tg.name) FROM idea_check_tags x JOIN tags tg ON tg.id = x.tag_id "
              + "         WHERE x.check_id = pic.id) AS pr_tags "
              + "FROM check_matches cm "
              + "LEFT JOIN corpus_projects cp ON cp.id = cm.corpus_project_id "
              + "LEFT JOIN domains d1 ON d1.id = cp.domain_id "
              + "LEFT JOIN project_types t1 ON t1.id = cp.type_id "
              + "LEFT JOIN proposals pr ON pr.id = cm.proposal_id "
              + "LEFT JOIN idea_checks pic ON pic.id = pr.check_id "
              + "LEFT JOIN domains d2 ON d2.id = pic.domain_id "
              + "LEFT JOIN project_types t2 ON t2.id = pic.type_id "
              + "WHERE cm.check_id = ? ORDER BY cm.final_score DESC, cm.rank_no";
        Set<String> mine = Set.copyOf(v.getTags());
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, v.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    MatchView m = new MatchView();
                    m.setId(rs.getInt("id"));
                    m.setPool(rs.getString("pool"));
                    boolean history = "HISTORY".equals(m.getPool());
                    m.setTargetId(history ? rs.getInt("corpus_project_id") : rs.getInt("proposal_id"));
                    m.setTitle(rs.getString("m_title"));
                    m.setAbstractText(rs.getString("m_abstract"));
                    m.setOrigin(history ? String.valueOf(rs.getInt("m_year")) : "Group " + rs.getString("m_group"));
                    m.setDomainName(rs.getString("m_domain"));
                    m.setTypeName(rs.getString("m_type"));
                    m.setTagScore(rs.getDouble("tag_score"));
                    m.setKeywordScore(rs.getDouble("keyword_score"));
                    m.setTypeScore(rs.getDouble("type_score"));
                    m.setRuleScore(rs.getDouble("rule_score"));
                    double concept = rs.getDouble("concept_score");
                    m.setConceptScore(rs.wasNull() ? null : concept);
                    m.setFinalScore(rs.getDouble("final_score"));
                    List<String> shared = new ArrayList<>();
                    for (String t : CorpusDao.splitTags(history ? rs.getString("cp_tags") : rs.getString("pr_tags"))) {
                        if (mine.contains(t)) shared.add(t);
                    }
                    m.setSharedTags(shared);
                    (history ? v.getHistory() : v.getClassMatches()).add(m);
                }
            }
        }
    }

    public void updateMatchConcept(Connection con, int matchId, double concept, double finalScore) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE check_matches SET concept_score = ?, final_score = ? WHERE id = ?")) {
            ps.setDouble(1, concept);
            ps.setDouble(2, finalScore);
            ps.setInt(3, matchId);
            ps.executeUpdate();
        }
    }

    public void updateOutcome(Connection con, int checkId, double finalScore, String verdict, boolean claimFlag,
                              String engineStatus) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE idea_checks SET final_score = ?, verdict = ?, claim_flag = ?, engine_status = ? WHERE id = ?")) {
            ps.setDouble(1, finalScore);
            ps.setString(2, verdict);
            ps.setBoolean(3, claimFlag);
            ps.setString(4, engineStatus);
            ps.setInt(5, checkId);
            ps.executeUpdate();
        }
    }

    public void setEngineStatus(int checkId, String status) throws SQLException {
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement("UPDATE idea_checks SET engine_status = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, checkId);
            ps.executeUpdate();
        }
    }

    /** Checks whose background comparison has not finished, oldest first. The maintenance job retries these. */
    public List<Integer> unfinished(int limit) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT id FROM idea_checks WHERE engine_status IN ('PENDING','FAILED','OFF') "
                   + "AND created_at < NOW() - INTERVAL 30 SECOND ORDER BY id LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getInt(1));
            }
        }
        return ids;
    }

    /** A student's own checks, newest first, from the v_check_history view. */
    public List<Map<String, Object>> history(int userId) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT id, created_at, title, domain_name, type_name, verdict, score_pct, claim_flag, closest_project "
                   + "FROM v_check_history WHERE user_id = ? ORDER BY created_at DESC, id DESC")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("id", rs.getInt("id"));
                    row.put("when", rs.getTimestamp("created_at").toLocalDateTime()
                            .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")));
                    row.put("title", rs.getString("title"));
                    row.put("domain", rs.getString("domain_name"));
                    row.put("type", rs.getString("type_name"));
                    row.put("verdict", rs.getString("verdict"));
                    row.put("score", rs.getInt("score_pct"));
                    row.put("claimFlag", rs.getBoolean("claim_flag"));
                    row.put("closest", rs.getString("closest_project"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    public int countAll() throws SQLException {
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM idea_checks");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
