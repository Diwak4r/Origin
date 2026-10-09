package np.edu.origin.dao;

import np.edu.origin.db.Database;
import np.edu.origin.model.IngestEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** The ingest log: which version of which file in the DB folder has been dealt with. */
public class IngestDao {

    public boolean seen(String fileName, long size, long modified) throws SQLException {
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT 1 FROM ingest_log WHERE file_name = ? AND file_size = ? AND file_modified = ? LIMIT 1")) {
            ps.setString(1, fileName);
            ps.setLong(2, size);
            ps.setLong(3, modified);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void record(String fileName, long size, long modified, String status, int added, int skipped, String note)
            throws SQLException {
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO ingest_log (file_name, file_size, file_modified, status, rows_added, rows_skipped, note) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, fileName);
            ps.setLong(2, size);
            ps.setLong(3, modified);
            ps.setString(4, status);
            ps.setInt(5, added);
            ps.setInt(6, skipped);
            ps.setString(7, note == null ? null : note.substring(0, Math.min(note.length(), 500)));
            ps.executeUpdate();
        }
    }

    public int countDone() throws SQLException {
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM ingest_log WHERE status = 'DONE'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public List<IngestEntry> recent(int limit) throws SQLException {
        List<IngestEntry> out = new ArrayList<>();
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT file_name, status, rows_added, rows_skipped, note, processed_at "
                   + "FROM ingest_log ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new IngestEntry(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4),
                            rs.getString(5), rs.getTimestamp(6).toLocalDateTime()));
                }
            }
        }
        return out;
    }
}
