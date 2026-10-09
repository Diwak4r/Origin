package np.edu.origin.dao;

import np.edu.origin.db.Database;
import np.edu.origin.engine.Candidate;
import np.edu.origin.engine.MatchIndex;
import np.edu.origin.engine.Vocabulary;
import np.edu.origin.model.ProjectRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The historical corpus of past projects. */
public class CorpusDao {

    private static final String WITH_TAGS =
            "SELECT cp.id, cp.title, cp.abstract, cp.year, cp.type_id, cp.source, "
          + "       GROUP_CONCAT(t.name ORDER BY t.name SEPARATOR ',') AS tag_list "
          + "FROM corpus_projects cp "
          + "LEFT JOIN corpus_project_tags cpt ON cpt.project_id = cp.id "
          + "LEFT JOIN tags t ON t.id = cpt.tag_id ";

    /** The index is rebuilt only when the number of projects changes or {@link #invalidateIndex()} is called. */
    private volatile MatchIndex cachedIndex;
    private volatile int cachedFor = -1;

    public synchronized MatchIndex index(Vocabulary vocab) throws SQLException {
        int n = count();
        if (cachedIndex == null || cachedFor != n) {
            cachedIndex = new MatchIndex(candidates(vocab));
            cachedFor = n;
        }
        return cachedIndex;
    }

    public synchronized void invalidateIndex() {
        cachedIndex = null;
    }

    public int count() throws SQLException {
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM corpus_projects");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Every corpus project in the form the engine compares. */
    public List<Candidate> candidates(Vocabulary vocab) throws SQLException {
        List<Candidate> out = new ArrayList<>();
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(WITH_TAGS + "GROUP BY cp.id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new Candidate(rs.getInt("id"), rs.getString("title"), rs.getInt("type_id"),
                        splitTags(rs.getString("tag_list")),
                        vocab.keywords(rs.getString("title"), rs.getString("abstract"))));
            }
        }
        return out;
    }

    /** Past projects sitting in one domain x type cell of the gap map, newest first. */
    public List<ProjectRow> inCell(int domainId, int typeId) throws SQLException {
        List<ProjectRow> out = new ArrayList<>();
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(WITH_TAGS
                     + "WHERE cp.domain_id = ? AND cp.type_id = ? GROUP BY cp.id ORDER BY cp.year DESC, cp.title")) {
            ps.setInt(1, domainId);
            ps.setInt(2, typeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ProjectRow(rs.getInt("id"), rs.getString("title"), rs.getString("abstract"),
                            rs.getInt("year"), rs.getString("source"), new ArrayList<>(splitTags(rs.getString("tag_list")))));
                }
            }
        }
        return out;
    }

    /** A bounded sample of the past corpus for the check form, newest first. */
    public List<ProjectRow> sample(int limit) throws SQLException {
        List<ProjectRow> out = new ArrayList<>();
        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(WITH_TAGS
                     + "GROUP BY cp.id ORDER BY cp.year DESC, cp.title LIMIT ?")) {
            ps.setInt(1, Math.max(1, Math.min(50, limit)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ProjectRow(rs.getInt("id"), rs.getString("title"), rs.getString("abstract"),
                            rs.getInt("year"), rs.getString("source"), new ArrayList<>(splitTags(rs.getString("tag_list")))));
                }
            }
        }
        return out;
    }

    /**
     * Sets the tech stack of an existing project that has none (a database made before the column existed).
     * Its topic links are dropped so the next linking pass reads the new text. Returns true when it changed a row.
     */
    public boolean backfillTech(Connection con, String title, int year, String tech) throws SQLException {
        int changed;
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE corpus_projects SET tech = ? WHERE LOWER(title) = LOWER(?) AND year = ? AND tech IS NULL")) {
            ps.setString(1, tech);
            ps.setString(2, title);
            ps.setInt(3, year);
            changed = ps.executeUpdate();
        }
        if (changed > 0) {
            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE cpt FROM corpus_project_topics cpt JOIN corpus_projects cp ON cp.id = cpt.project_id "
                  + "WHERE LOWER(cp.title) = LOWER(?) AND cp.year = ?")) {
                ps.setString(1, title);
                ps.setInt(2, year);
                ps.executeUpdate();
            }
        }
        return changed > 0;
    }

    /** True when a project with this title (any letter case) and year is already in the corpus. */
    public boolean exists(Connection con, String title, int year) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT 1 FROM corpus_projects WHERE LOWER(title) = LOWER(?) AND year = ? LIMIT 1")) {
            ps.setString(1, title);
            ps.setInt(2, year);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Inserts one seed project with its tags. The tag triggers update the counters. */
    public int insert(Connection con, String title, String abstractText, int year, int domainId, int typeId,
                      Map<String, Integer> tagIds) throws SQLException {
        return insert(con, title, abstractText, year, domainId, typeId, tagIds, "SEED");
    }

    /** {@code source} is SEED, ARCHIVED or IMPORTED (read from the DB folder). */
    public int insert(Connection con, String title, String abstractText, int year, int domainId, int typeId,
                      Map<String, Integer> tagIds, String source) throws SQLException {
        return insert(con, title, abstractText, year, domainId, typeId, tagIds, source, null);
    }

    /** As above, with the tech stack the project was built with (null when unknown). */
    public int insert(Connection con, String title, String abstractText, int year, int domainId, int typeId,
                      Map<String, Integer> tagIds, String source, String tech) throws SQLException {
        int id;
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO corpus_projects (title, abstract, year, domain_id, type_id, source, tech) VALUES (?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setString(2, abstractText);
            ps.setInt(3, year);
            ps.setInt(4, domainId);
            ps.setInt(5, typeId);
            ps.setString(6, source);
            ps.setString(7, tech == null || tech.isBlank() ? null : tech.strip().substring(0, Math.min(200, tech.strip().length())));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                id = keys.getInt(1);
            }
        }
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO corpus_project_tags (project_id, tag_id) VALUES (?, ?)")) {
            for (int tagId : tagIds.values()) {
                ps.setInt(1, id);
                ps.setInt(2, tagId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        return id;
    }

    static Set<String> splitTags(String list) {
        Set<String> out = new LinkedHashSet<>();
        if (list == null || list.isBlank()) return out;
        for (String t : list.split(",")) out.add(t.trim());
        return out;
    }
}
