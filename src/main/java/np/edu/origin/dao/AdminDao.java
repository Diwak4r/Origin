package np.edu.origin.dao;

import np.edu.origin.db.Database;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Supervisor-only data: overrides, the audit trail, statistics and the two stored procedures. */
public class AdminDao {

    /** A remembered supervisor verdict for this idea fingerprint and corpus project, or null. */
    public Boolean findOverride(Connection con, String fingerprint, int corpusProjectId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT same_project FROM match_overrides WHERE fingerprint = ? AND corpus_project_id = ?")) {
            ps.setString(1, fingerprint);
            ps.setInt(2, corpusProjectId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBoolean(1) : null;
            }
        }
    }

    public void saveOverride(Connection con, String fingerprint, int corpusProjectId, boolean same, int supervisorId)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO match_overrides (fingerprint, corpus_project_id, same_project, supervisor_id) VALUES (?, ?, ?, ?) "
              + "ON DUPLICATE KEY UPDATE same_project = VALUES(same_project), supervisor_id = VALUES(supervisor_id), "
              + "created_at = NOW()")) {
            ps.setString(1, fingerprint);
            ps.setInt(2, corpusProjectId);
            ps.setBoolean(3, same);
            ps.setInt(4, supervisorId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO audit_log (actor_id, action, entity, entity_id, detail) VALUES (?, 'OVERRIDE', 'corpus_project', ?, ?)")) {
            ps.setInt(1, supervisorId);
            ps.setInt(2, corpusProjectId);
            ps.setString(3, same ? "Marked as the same project" : "Marked as a different project");
            ps.executeUpdate();
        }
    }

    /** Calls sp_archive_semester and returns how many approved projects moved into the corpus. */
    public int archiveSemester(String newCode, String newLabel, int actorId) throws SQLException {
        try (Connection con = Database.connect();
             CallableStatement cs = con.prepareCall("{CALL sp_archive_semester(?, ?, ?, ?)}")) {
            cs.setString(1, newCode);
            cs.setString(2, newLabel);
            cs.setInt(3, actorId);
            cs.registerOutParameter(4, Types.INTEGER);
            cs.execute();
            return cs.getInt(4);
        }
    }

    /** Calls sp_semester_report. Each row is column name -> value, in column order. */
    public List<Map<String, Object>> semesterReport() throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection con = Database.connect();
             CallableStatement cs = con.prepareCall("{CALL sp_semester_report()}");
             ResultSet rs = cs.executeQuery()) {
            ResultSetMetaData md = rs.getMetaData();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= md.getColumnCount(); i++) row.put(md.getColumnLabel(i), rs.getObject(i));
                rows.add(row);
            }
        }
        return rows;
    }

    public List<Map<String, Object>> recentAudit(int limit) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        DateTimeFormatter when = DateTimeFormatter.ofPattern("d MMM, HH:mm");
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT a.at, a.action, a.detail, u.full_name FROM audit_log a LEFT JOIN users u ON u.id = a.actor_id "
                   + "ORDER BY a.id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("when", rs.getTimestamp("at").toLocalDateTime().format(when));
                    row.put("action", rs.getString("action"));
                    row.put("detail", rs.getString("detail"));
                    String who = rs.getString("full_name");
                    row.put("who", who == null ? "Database" : who);
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /** Tags students are checking most, next to how often they appear in past projects. */
    public List<Map<String, Object>> tagTrends(int limit) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT name, check_uses, corpus_uses FROM tags WHERE check_uses > 0 "
                   + "ORDER BY check_uses DESC, corpus_uses DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("tag", rs.getString(1));
                    row.put("checks", rs.getInt(2));
                    row.put("corpus", rs.getInt(3));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /** Rows of the v_domain_saturation view, most crowded first. */
    public List<Map<String, Object>> saturation() throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT domain_name, project_count, last_year, share_pct FROM v_domain_saturation "
                   + "ORDER BY project_count DESC, domain_name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("domain", rs.getString(1));
                row.put("projects", rs.getInt(2));
                int last = rs.getInt(3);
                row.put("lastYear", rs.wasNull() ? "none" : String.valueOf(last));
                row.put("share", rs.getDouble(4));
                rows.add(row);
            }
        }
        return rows;
    }
}
