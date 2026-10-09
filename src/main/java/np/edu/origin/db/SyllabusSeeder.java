package np.edu.origin.db;

import np.edu.origin.dao.CorpusDao;
import np.edu.origin.dao.SyllabusDao;
import np.edu.origin.model.Topic;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads the syllabus topics, and the topics and one-line twist of every seeded problem. It is safe
 * to run on every start: topics load only into an empty table, and a problem is linked only once,
 * so databases created before the syllabus layer existed are upgraded on their next start.
 */
public class SyllabusSeeder {

    private final SyllabusDao dao;
    private final CorpusDao corpus;

    public SyllabusSeeder(SyllabusDao dao, CorpusDao corpus) {
        this.dao = dao;
        this.corpus = corpus;
    }

    public void run() throws IOException, SQLException {
        if (dao.topicCount() == 0) loadTopics();
        linkProblems();
        backfillTech();
    }

    /** Gives projects seeded before the tech column existed their stack, so their syllabus topics can be read. */
    private void backfillTech() throws IOException, SQLException {
        try (Connection con = Database.connect()) {
            con.setAutoCommit(false);
            for (String[] c : Seeder.readCsv("seed/projects.csv")) {
                // title|year|domain|type|tags|abstract|stack
                if (c.length > 6 && !c[6].isBlank()) corpus.backfillTech(con, c[0], Integer.parseInt(c[1]), c[6]);
            }
            con.commit();
        }
    }

    private void loadTopics() throws IOException, SQLException {
        try (Connection con = Database.connect()) {
            con.setAutoCommit(false);
            for (String[] c : Seeder.readCsv("seed/syllabus_topics.csv")) {
                // course|unit|name|crud|keywords
                dao.insertTopic(con, c[0], Integer.parseInt(c[1]), c[2], "1".equals(c[3]), c[4]);
            }
            con.commit();
        }
    }

    private void linkProblems() throws IOException, SQLException {
        Map<String, Integer> topicIds = new HashMap<>();
        for (Topic t : dao.topics()) topicIds.put(t.getName(), t.getId());
        try (Connection con = Database.connect()) {
            con.setAutoCommit(false);
            for (String[] c : Seeder.readCsv("seed/problems.csv")) {
                // domain|statement|affected|solution|primary_type|alt_type|tags|topics|twist
                if (c.length < 9) continue;
                Integer problemId = dao.problemIdByStatement(c[1]);
                if (problemId == null || dao.problemHasTopics(problemId)) continue;
                Set<Integer> ids = new LinkedHashSet<>();
                for (String name : c[7].split(";")) {
                    Integer id = topicIds.get(name.strip());
                    if (id == null) throw new SQLException("problems.csv names an unknown syllabus topic: " + name);
                    ids.add(id);
                }
                dao.linkProblem(con, problemId, ids, c[8]);
            }
            con.commit();
        }
    }
}
