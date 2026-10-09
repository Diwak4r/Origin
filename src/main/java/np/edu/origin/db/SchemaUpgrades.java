package np.edu.origin.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Changes that "CREATE TABLE IF NOT EXISTS" cannot make to a table that already exists, applied
 * once and only when needed, so a database created by an older version keeps working.
 */
public final class SchemaUpgrades {

    private SchemaUpgrades() { }

    public static void run(Connection con) throws SQLException {
        if (!hasColumn(con, "corpus_projects", "tech")) {
            try (Statement st = con.createStatement()) {
                st.execute("ALTER TABLE corpus_projects ADD COLUMN tech VARCHAR(200) NULL AFTER type_id");
            }
        }
        if (!hasColumn(con, "idea_checks", "target_semester")) {
            try (Statement st = con.createStatement()) {
                st.execute("ALTER TABLE idea_checks ADD COLUMN target_semester TINYINT NOT NULL DEFAULT 4 AFTER type_id");
            }
        }
    }

    static boolean hasColumn(Connection con, String table, String column) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?")) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}