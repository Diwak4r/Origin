package np.edu.origin.dao;

import np.edu.origin.db.Database;
import np.edu.origin.decision.DecisionClient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Remembers every decision-engine answer in the engine_cache table. */
public class CacheDao implements DecisionClient.Cache {

    @Override
    public String get(String key) {
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement("SELECT answer_json FROM engine_cache WHERE cache_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            // A cache miss only costs one extra call, so a read failure is not fatal.
            System.err.println("[origin] engine cache read failed: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void put(String key, String answerJson, String model) {
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT IGNORE INTO engine_cache (cache_key, answer_json, model) VALUES (?, ?, ?)")) {
            ps.setString(1, key);
            ps.setString(2, answerJson);
            ps.setString(3, model);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[origin] engine cache write failed: " + e.getMessage());
        }
    }

    public int count() throws SQLException {
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM engine_cache");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
