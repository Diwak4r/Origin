package np.edu.origin.dao;

import np.edu.origin.db.Database;
import np.edu.origin.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

/** Accounts. The hash and salt never leave this class except for the login check. */
public class UserDao {

    /** A stored account together with its password hash and salt. */
    public record Stored(User user, String hash, String salt) { }

    public Stored findByEmail(String email) throws SQLException {
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT id, full_name, email, role, group_code, password_hash, salt FROM users WHERE email = ?")) {
            ps.setString(1, email.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                User u = new User(rs.getInt("id"), rs.getString("full_name"), rs.getString("email"),
                        rs.getString("role"), rs.getString("group_code"));
                return new Stored(u, rs.getString("password_hash"), rs.getString("salt"));
            }
        }
    }

    public boolean emailExists(String email) throws SQLException {
        return findByEmail(email) != null;
    }

    public int insert(String fullName, String email, String hash, String salt, String role, String groupCode)
            throws SQLException {
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO users (full_name, email, password_hash, salt, role, group_code) VALUES (?, ?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, fullName);
            ps.setString(2, email.toLowerCase());
            ps.setString(3, hash);
            ps.setString(4, salt);
            ps.setString(5, role);
            if (groupCode == null) ps.setNull(6, Types.VARCHAR); else ps.setString(6, groupCode);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public int count() throws SQLException {
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM users");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
