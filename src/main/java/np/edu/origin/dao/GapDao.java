package np.edu.origin.dao;

import np.edu.origin.db.Database;
import np.edu.origin.model.GapCell;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Reads the v_gap_matrix view: every domain crossed with every project type. */
public class GapDao {

    public List<GapCell> matrix() throws SQLException {
        List<GapCell> out = new ArrayList<>();
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT * FROM v_gap_matrix ORDER BY domain_name, type_id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new GapCell(rs.getInt("domain_id"), rs.getString("domain_name"), rs.getString("short_name"),
                        rs.getInt("type_id"), rs.getString("type_name"), rs.getInt("project_count"),
                        rs.getInt("live_count"), rs.getInt("open_ideas")));
            }
        }
        return out;
    }
}
