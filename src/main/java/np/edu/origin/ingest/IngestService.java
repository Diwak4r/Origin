package np.edu.origin.ingest;

import np.edu.origin.dao.CorpusDao;
import np.edu.origin.dao.IngestDao;
import np.edu.origin.dao.LookupDao;
import np.edu.origin.db.Database;
import np.edu.origin.engine.Vocabulary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads one file from the DB folder and adds its projects to the corpus in a single transaction:
 * either every new row goes in or none does. Rows already in the corpus (same title and year) are
 * skipped, so dropping the same file twice, or two files with overlapping rows, is harmless.
 */
public class IngestService {

    /** @param status DONE, FAILED or UNSUPPORTED */
    public record Outcome(String status, int added, int skipped, String note) { }

    private static final int MAX_PROBLEMS_IN_NOTE = 3;

    private final LookupDao lookups;
    private final CorpusDao corpus;
    private final IngestDao log;
    private final Runnable afterIngest;

    /** {@code afterIngest} runs when at least one project was added (the app refreshes the idea suggestions). */
    public IngestService(LookupDao lookups, CorpusDao corpus, IngestDao log, Runnable afterIngest) {
        this.lookups = lookups;
        this.corpus = corpus;
        this.log = log;
        this.afterIngest = afterIngest;
    }

    /** Never throws for a bad file: the outcome and the reason go to the ingest log. */
    public Outcome ingest(Path file) throws SQLException, IOException {
        String name = file.getFileName().toString();
        long size = Files.size(file);
        long modified = Files.getLastModifiedTime(file).toMillis();

        TableReader reader = TableReader.forFile(file);
        Outcome outcome;
        if (reader == null) {
            outcome = new Outcome("UNSUPPORTED", 0, 0, "Unsupported format. Only CSV files are read.");
        } else {
            try {
                outcome = load(ProjectParser.parse(reader.read(file)));
            } catch (IOException e) {
                outcome = new Outcome("FAILED", 0, 0, e.getMessage());
            } catch (SQLDataException | SQLIntegrityConstraintViolationException e) {
                // The data itself is wrong. Any other SQL error (database down) propagates, so the file is retried.
                outcome = new Outcome("FAILED", 0, 0, "A row was rejected by the database, nothing was added: " + e.getMessage());
            }
        }
        log.record(name, size, modified, outcome.status(), outcome.added(), outcome.skipped(), outcome.note());
        if (outcome.added() > 0) {
            corpus.invalidateIndex();
            afterIngest.run();
        }
        return outcome;
    }

    private Outcome load(ProjectParser.Result parsed) throws SQLException {
        Vocabulary vocab = lookups.vocabulary();
        Classifier classifier = new Classifier(lookups.domains(), lookups.types());
        int added = 0;
        int duplicates = 0;
        try (Connection con = Database.connect()) {
            con.setAutoCommit(false);
            try {
                for (ProjectRecord r : parsed.records()) {
                    if (corpus.exists(con, r.title(), r.year())) {
                        duplicates++;
                        continue;
                    }
                    String abstractText = r.abstractText().isBlank() ? r.title() : r.abstractText();
                    String text = r.title() + " " + abstractText + " " + r.tech() + " " + r.tags();
                    Set<String> tags = r.tags().isBlank() ? vocab.tagsFromText(r.title(), abstractText)
                                                          : vocab.canonicalTags(r.tags());
                    Map<String, Integer> tagIds = lookups.ensureTags(con, tags);
                    corpus.insert(con, r.title(), abstractText, r.year(), classifier.domainId(r.domain(), text),
                            classifier.typeId(r.type(), r.tech() + " " + r.title() + " " + abstractText), tagIds, "IMPORTED", r.tech());
                    added++;
                }
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
        int skipped = duplicates + parsed.problems().size();
        return new Outcome("DONE", added, skipped, note(added, duplicates, parsed.problems()));
    }

    private static String note(int added, int duplicates, List<String> problems) {
        List<String> parts = new ArrayList<>();
        parts.add("Added " + added + (added == 1 ? " project." : " projects."));
        if (duplicates > 0) parts.add(duplicates + " already in the corpus.");
        if (!problems.isEmpty()) {
            List<String> shown = problems.subList(0, Math.min(MAX_PROBLEMS_IN_NOTE, problems.size()));
            String more = problems.size() > shown.size() ? " (+" + (problems.size() - shown.size()) + " more)" : "";
            parts.add("Skipped: " + String.join("; ", shown) + more + ".");
        }
        return String.join(" ", parts);
    }
}
