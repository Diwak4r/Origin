package np.edu.origin.dao;

import np.edu.origin.db.Database;
import np.edu.origin.engine.Candidate;
import np.edu.origin.engine.Vocabulary;
import np.edu.origin.model.ProposalView;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Class Radar: the ideas groups have locked this semester, and the clashes between them. */
public class ProposalDao {

    /** Locked ideas of the active semester, as engine candidates. The id is the proposal id. */
    public List<Candidate> activeCandidates(Vocabulary vocab, String excludeGroup) throws SQLException {
        List<Candidate> out = new ArrayList<>();
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT p.id, ic.title, ic.abstract, ic.type_id, "
                   + "       GROUP_CONCAT(t.name ORDER BY t.name SEPARATOR ',') AS tag_list "
                   + "FROM proposals p "
                   + "JOIN semesters s ON s.id = p.semester_id AND s.is_active "
                   + "JOIN idea_checks ic ON ic.id = p.check_id "
                   + "LEFT JOIN idea_check_tags ict ON ict.check_id = ic.id "
                   + "LEFT JOIN tags t ON t.id = ict.tag_id "
                   + "WHERE p.status <> 'REJECTED' AND (? IS NULL OR p.group_code <> ?) "
                   + "GROUP BY p.id")) {
            ps.setString(1, excludeGroup);
            ps.setString(2, excludeGroup);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Candidate(rs.getInt("id"), rs.getString("title"), rs.getInt("type_id"),
                            CorpusDao.splitTags(rs.getString("tag_list")),
                            vocab.keywords(rs.getString("title"), rs.getString("abstract"))));
                }
            }
        }
        return out;
    }

    /** proposal id -> group code for the active semester. */
    public Map<Integer, String> groupCodes() throws SQLException {
        Map<Integer, String> out = new java.util.HashMap<>();
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT p.id, p.group_code FROM proposals p JOIN semesters s ON s.id = p.semester_id AND s.is_active");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.put(rs.getInt(1), rs.getString(2));
        }
        return out;
    }

    /** The group's proposal id for the active semester, or null. */
    public Integer findForGroup(String groupCode) throws SQLException {
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT p.id FROM proposals p JOIN semesters s ON s.id = p.semester_id AND s.is_active "
                   + "WHERE p.group_code = ?")) {
            ps.setString(1, groupCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    public int insert(Connection con, String groupCode, int semesterId, int checkId, int userId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO proposals (group_code, semester_id, check_id, locked_by) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, groupCode);
            ps.setInt(2, semesterId);
            ps.setInt(3, checkId);
            ps.setInt(4, userId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public void insertCollision(Connection con, int olderProposal, int newerProposal, double score) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT IGNORE INTO radar_collisions (proposal_a, proposal_b, score) VALUES (?, ?, ?)")) {
            ps.setInt(1, olderProposal);
            ps.setInt(2, newerProposal);
            ps.setDouble(3, score);
            ps.executeUpdate();
        }
    }

    /** The Class Radar list from the v_class_radar view, with the groups each proposal clashes with. */
    public List<ProposalView> radar(String statusFilter) throws SQLException {
        Map<Integer, ProposalView> byId = new LinkedHashMap<>();
        try (Connection con = Database.connect()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT * FROM v_class_radar WHERE (? IS NULL OR status = ?) ORDER BY group_code")) {
                ps.setString(1, statusFilter);
                ps.setString(2, statusFilter);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ProposalView p = new ProposalView();
                        p.setId(rs.getInt("proposal_id"));
                        p.setGroupCode(rs.getString("group_code"));
                        p.setStatus(rs.getString("status"));
                        p.setLockedAt(rs.getTimestamp("locked_at").toLocalDateTime());
                        p.setCheckId(rs.getInt("check_id"));
                        p.setTitle(rs.getString("title"));
                        p.setAbstractText(rs.getString("abstract"));
                        p.setVerdict(rs.getString("verdict"));
                        p.setFinalScore(rs.getDouble("final_score"));
                        p.setDomainName(rs.getString("domain_name"));
                        p.setTypeName(rs.getString("type_name"));
                        p.setLockedByName(rs.getString("locked_by_name"));
                        p.setCollisions(rs.getInt("collisions"));
                        byId.put(p.getId(), p);
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT u.group_code, u.full_name FROM users u "
                  + "WHERE u.role = 'STUDENT' AND u.group_code IN "
                  + "(SELECT p.group_code FROM proposals p JOIN semesters s ON s.id = p.semester_id AND s.is_active) "
                  + "ORDER BY u.group_code, u.full_name");
                 ResultSet rs = ps.executeQuery()) {
                Map<String, List<String>> members = new LinkedHashMap<>();
                while (rs.next()) {
                    members.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2));
                }
                for (ProposalView p : byId.values()) {
                    p.getMemberNames().addAll(members.getOrDefault(p.getGroupCode(), List.of()));
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT rc.proposal_a, rc.proposal_b, rc.score, pa.group_code AS ga, pb.group_code AS gb "
                  + "FROM radar_collisions rc JOIN proposals pa ON pa.id = rc.proposal_a "
                  + "JOIN proposals pb ON pb.id = rc.proposal_b ORDER BY rc.score DESC");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int pct = (int) Math.round(rs.getDouble("score") * 100);
                    ProposalView a = byId.get(rs.getInt("proposal_a"));
                    ProposalView b = byId.get(rs.getInt("proposal_b"));
                    if (a != null) a.getCollidesWith().add(rs.getString("gb") + " (" + pct + "%)");
                    if (b != null) b.getCollidesWith().add(rs.getString("ga") + " (" + pct + "%)");
                }
            }
        }
        return new ArrayList<>(byId.values());
    }

    public void review(int proposalId, String status, int supervisorId, String note) throws SQLException {
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE proposals SET status = ?, reviewed_by = ?, reviewed_at = NOW(), review_note = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, supervisorId);
            ps.setString(3, note == null || note.isBlank() ? null : note.trim());
            ps.setInt(4, proposalId);
            if (ps.executeUpdate() == 0) throw new SQLException("Proposal " + proposalId + " not found");
        }
    }

    public int count() throws SQLException {
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COUNT(*) FROM proposals p JOIN semesters s ON s.id = p.semester_id AND s.is_active");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
