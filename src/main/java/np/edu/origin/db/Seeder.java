package np.edu.origin.db;

import np.edu.origin.dao.CorpusDao;
import np.edu.origin.dao.IdeaDao;
import np.edu.origin.dao.LookupDao;
import np.edu.origin.dao.UserDao;
import np.edu.origin.engine.Vocabulary;
import np.edu.origin.model.IdeaInput;
import np.edu.origin.model.Lookup;
import np.edu.origin.model.User;
import np.edu.origin.service.CheckService;
import np.edu.origin.service.NotFoundException;
import np.edu.origin.service.PasswordHasher;
import np.edu.origin.service.RadarService;
import np.edu.origin.service.ValidationException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * First-start setup. The schema and reference data run on every start (they are safe to
 * re-run); the CSV seed data loads only into an empty database.
 */
public class Seeder {

    public static final String DEMO_PASSWORD = "Origin@2026";
    private static final Pattern PIPE = Pattern.compile("\\|");

    private final LookupDao lookups;
    private final CorpusDao corpus;
    private final IdeaDao ideas;
    private final UserDao users;
    private final CheckService checks;
    private final RadarService radar;

    public Seeder(LookupDao lookups, CorpusDao corpus, IdeaDao ideas, UserDao users, CheckService checks,
                  RadarService radar) {
        this.lookups = lookups;
        this.corpus = corpus;
        this.ideas = ideas;
        this.users = users;
        this.checks = checks;
        this.radar = radar;
    }

    public void run() throws IOException, SQLException {
        try (Connection con = Database.connect()) {
            SchemaRunner.run(con, "db/schema.sql");
            SchemaUpgrades.run(con);
            SchemaRunner.run(con, "db/reference.sql");
        }
        LookupDao.clearCache();
        if (corpus.count() == 0) {
            loadProjects();
            loadProblems();
            LookupDao.clearCache();
        }
        if (users.count() == 0) {
            createAccounts();
            loadClassProposals();
        }
    }

    /** Reads a pipe-separated seed file from the classpath, skipping the header line. */
    static List<String[]> readCsv(String resource) throws IOException {
        InputStream in = Seeder.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) throw new IOException("Missing seed file " + resource);
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line = br.readLine();                      // header
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                rows.add(PIPE.split(line, -1));
            }
        }
        return rows;
    }

    private Map<String, Integer> domainIds() throws SQLException {
        Map<String, Integer> m = new HashMap<>();
        for (Lookup d : lookups.domains()) m.put(d.getCode(), d.getId());
        return m;
    }

    private Map<String, Integer> typeIds() throws SQLException {
        Map<String, Integer> m = new HashMap<>();
        for (Lookup t : lookups.types()) m.put(t.getName(), t.getId());
        return m;
    }

    private void loadProjects() throws IOException, SQLException {
        Map<String, Integer> domains = domainIds();
        Map<String, Integer> types = typeIds();
        Vocabulary vocab = lookups.vocabulary();
        try (Connection con = Database.connect()) {
            con.setAutoCommit(false);
            for (String[] c : readCsv("seed/projects.csv")) {
                // title|year|domain|type|tags|abstract|stack
                Map<String, Integer> tagIds = lookups.ensureTags(con, vocab.canonicalTags(c[4]));
                corpus.insert(con, c[0], c[5], Integer.parseInt(c[1]), domains.get(c[2]), types.get(c[3]), tagIds, "SEED",
                        c.length > 6 && !c[6].isBlank() ? c[6] : null);
            }
            con.commit();
        }
    }

    private void loadProblems() throws IOException, SQLException {
        Map<String, Integer> domains = domainIds();
        Map<String, Integer> types = typeIds();
        Vocabulary vocab = lookups.vocabulary();
        try (Connection con = Database.connect()) {
            con.setAutoCommit(false);
            for (String[] c : readCsv("seed/problems.csv")) {
                // domain|statement|affected|solution|primary_type|alt_type|tags
                Map<String, Integer> tagIds = lookups.ensureTags(con, vocab.canonicalTags(c[6]));
                Integer alt = c[5].isBlank() ? null : types.get(c[5]);
                ideas.insertProblem(con, domains.get(c[0]), c[1], c[2], c[3], types.get(c[4]), alt, tagIds);
            }
            con.commit();
        }
    }

    private void createAccounts() throws SQLException {
        account("Supervisor Demo", "supervisor@origin.edu", "SUPERVISOR", null);
        account("Sandesh Dotel", "sandesh@origin.edu", "STUDENT", "G19");
        account("Bhumika Karki", "bhumika@origin.edu", "STUDENT", "G19");
        account("Diwakar Ray Yadav", "diwakar@origin.edu", "STUDENT", "G19");
    }

    private int account(String name, String email, String role, String group) throws SQLException {
        String salt = PasswordHasher.newSalt();
        return users.insert(name, email, PasswordHasher.hash(DEMO_PASSWORD, salt), salt, role, group);
    }

    /** The class list locks its ideas through the real check and lock code, so the radar shows real scores. */
    private void loadClassProposals() throws IOException, SQLException {
        Map<String, Integer> domains = domainIds();
        Map<String, Integer> types = typeIds();
        for (String[] c : readCsv("seed/class_proposals.csv")) {
            // group|name|email|title|domain|type|tags|problem|abstract
            int id = account(c[1], c[2], "STUDENT", c[0]);
            User u = new User(id, c[1], c[2], "STUDENT", c[0]);
            IdeaInput in = new IdeaInput(c[3], c[8], c[7], c[6], domains.get(c[4]), types.get(c[5]), 4);
            int checkId;
            try {
                checkId = checks.check(u, in);
            } catch (ValidationException e) {
                throw new SQLException("Seed idea for " + c[0] + " is invalid: " + e.getMessage(), e);
            }
            try {
                radar.lockForSeed(u, checkId);
            } catch (ValidationException | NotFoundException e) {
                // The lock was refused for another reason. Keep the check, skip the lock.
                System.err.println("[origin] seed group " + c[0] + " not locked: " + e.getMessage());
            }
        }
    }
}
