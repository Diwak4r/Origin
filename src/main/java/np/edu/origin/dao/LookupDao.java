package np.edu.origin.dao;

import np.edu.origin.db.Database;
import np.edu.origin.engine.Vocabulary;
import np.edu.origin.model.Lookup;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reference data: domains, project types, the active semester and the tag vocabulary. */
public class LookupDao {

    /** The vocabulary is read often and changes rarely, so it is cached until a new tag appears. */
    private static volatile Vocabulary cachedVocabulary;

    public List<Lookup> domains() throws SQLException {
        return list("SELECT id, name, short_name FROM domains ORDER BY name");
    }

    public List<Lookup> types() throws SQLException {
        return list("SELECT id, name, CAST(effort AS CHAR) FROM project_types ORDER BY id");
    }

    private List<Lookup> list(String sql) throws SQLException {
        List<Lookup> out = new ArrayList<>();
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(new Lookup(rs.getInt(1), rs.getString(2), rs.getString(3)));
        }
        return out;
    }

    public int activeSemesterId() throws SQLException {
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement("SELECT id FROM semesters WHERE is_active LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) throw new SQLException("No active semester");
            return rs.getInt(1);
        }
    }

    public String activeSemesterLabel() throws SQLException {
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement("SELECT label FROM semesters WHERE is_active LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : "";
        }
    }

    /** When the most recent semester was archived, or null if none has been archived yet. */
    public java.time.LocalDateTime lastArchiveTime() throws SQLException {
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement("SELECT MAX(archived_at) FROM semesters");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            java.sql.Timestamp t = rs.getTimestamp(1);
            return t == null ? null : t.toLocalDateTime();
        }
    }

    public Vocabulary vocabulary() throws SQLException {
        Vocabulary v = cachedVocabulary;
        if (v != null) return v;
        Map<String, String> synonyms = new HashMap<>();
        List<String> tags = new ArrayList<>();
        try (Connection con = Database.connect()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT s.alias, t.name FROM tag_synonyms s JOIN tags t ON t.id = s.tag_id");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) synonyms.put(rs.getString(1), rs.getString(2));
            }
            try (PreparedStatement ps = con.prepareStatement("SELECT name FROM tags");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) tags.add(rs.getString(1));
            }
        }
        v = new Vocabulary(synonyms, tags);
        cachedVocabulary = v;
        return v;
    }

    /** All tag names, most used first. Feeds the tag suggestions under the form field. */
    public List<String> tagNames() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT name FROM tags ORDER BY corpus_uses + check_uses DESC, name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(rs.getString(1));
        }
        return out;
    }

    /**
     * Makes sure every tag exists and returns name -> id. New tags are inserted, so the
     * vocabulary grows with the students' own words. Runs inside the caller's transaction.
     */
    public Map<String, Integer> ensureTags(Connection con, Set<String> names) throws SQLException {
        Map<String, Integer> ids = new HashMap<>();
        boolean added = false;
        try (PreparedStatement find = con.prepareStatement("SELECT id FROM tags WHERE name = ?");
             PreparedStatement insert = con.prepareStatement("INSERT INTO tags (name) VALUES (?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            for (String name : names) {
                find.setString(1, name);
                try (ResultSet rs = find.executeQuery()) {
                    if (rs.next()) {
                        ids.put(name, rs.getInt(1));
                        continue;
                    }
                }
                insert.setString(1, name);
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    keys.next();
                    ids.put(name, keys.getInt(1));
                }
                added = true;
            }
        }
        if (added) cachedVocabulary = null;
        return ids;
    }

    public static void clearCache() {
        cachedVocabulary = null;
    }
}
