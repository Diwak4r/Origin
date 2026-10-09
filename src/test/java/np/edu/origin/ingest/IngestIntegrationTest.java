package np.edu.origin.ingest;

import np.edu.origin.config.AppConfig;
import np.edu.origin.dao.CorpusDao;
import np.edu.origin.dao.IngestDao;
import np.edu.origin.dao.LookupDao;
import np.edu.origin.db.Database;
import np.edu.origin.db.SchemaRunner;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drops real files into a folder and checks the corpus, against a MySQL database named origin_ingest_test. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IngestIntegrationTest {

    private static final String URL =
            "jdbc:mysql://localhost:3306/origin_ingest_test?createDatabaseIfNotExist=true&serverTimezone=Asia/Kathmandu";

    static LookupDao lookups = new LookupDao();
    static CorpusDao corpus = new CorpusDao();
    static IngestDao log = new IngestDao();
    static final AtomicInteger refreshes = new AtomicInteger();
    static IngestService service;

    @TempDir static Path folder;
    static DbFolderWatcher watcher;

    @BeforeAll
    static void buildDatabase() throws Exception {
        try (Connection c = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/?serverTimezone=Asia/Kathmandu", AppConfig.dbUser(), AppConfig.dbPassword());
             Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS origin_ingest_test");
        } catch (SQLException e) {
            Assumptions.abort("MySQL not reachable, ingest integration tests skipped: " + e.getMessage());
        }
        Database.configure(URL, AppConfig.dbUser(), AppConfig.dbPassword());
        LookupDao.clearCache();
        try (Connection con = Database.connect()) {
            SchemaRunner.run(con, "db/schema.sql");
            SchemaRunner.run(con, "db/reference.sql");
        }
        service = new IngestService(lookups, corpus, log, refreshes::incrementAndGet);
        watcher = new DbFolderWatcher(folder, service, log);
    }

    static int count(String sql, Object... args) throws SQLException {
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    static final String CSV = "Project Name,Year,Description,Tech Stack\n"
            + "Hospital Appointment Desk,2022,\"Patients book a doctor slot and get an SMS reminder\",Java MySQL\n"
            + "Farm Price Board,2023,\"Farmers see daily market prices for crops\",PHP\n"
            + "Chess Duel,2021,\"A two player game with a computer opponent\",Unity game\n"
            + ",2020,No title here,Java\n";

    @Test @Order(1)
    void aDroppedFileIsReadOnTheSecondScanAndTheProjectsAppear() throws Exception {
        Files.writeString(folder.resolve("batch2024.csv"), CSV, StandardCharsets.UTF_8);
        assertEquals(0, watcher.scan(), "first sight: wait one scan to be sure the file is complete");
        assertEquals(0, count("SELECT COUNT(*) FROM corpus_projects"));
        assertEquals(1, watcher.scan(), "unchanged since the last scan: read it now");

        assertEquals(3, count("SELECT COUNT(*) FROM corpus_projects WHERE source = 'IMPORTED'"));
        assertEquals(1, count("SELECT COUNT(*) FROM ingest_log WHERE status = 'DONE' AND rows_added = 3 AND rows_skipped = 1"));
        assertEquals(1, refreshes.get(), "suggestions are refreshed once after an ingest that added projects");
    }

    @Test @Order(2)
    void missingDomainTypeAndTagsAreFilledIn() throws Exception {
        assertEquals("Health", one("SELECT d.name FROM corpus_projects c JOIN domains d ON d.id = c.domain_id WHERE c.title = 'Hospital Appointment Desk'"));
        assertEquals("Agriculture", one("SELECT d.name FROM corpus_projects c JOIN domains d ON d.id = c.domain_id WHERE c.title = 'Farm Price Board'"));
        assertEquals("Game", one("SELECT t.name FROM corpus_projects c JOIN project_types t ON t.id = c.type_id WHERE c.title = 'Chess Duel'"));
        assertTrue(count("SELECT COUNT(*) FROM corpus_project_tags cpt JOIN corpus_projects c ON c.id = cpt.project_id "
                + "WHERE c.title = 'Hospital Appointment Desk'") >= 1, "tags come from the text");
    }

    static String one(String sql) throws SQLException {
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    @Test @Order(3)
    void theSameFileAgainAddsNothing() throws Exception {
        watcher.scan();
        watcher.scan();
        assertEquals(3, count("SELECT COUNT(*) FROM corpus_projects"));
        assertEquals(1, count("SELECT COUNT(*) FROM ingest_log"), "an unchanged file is not logged again");
    }

    @Test @Order(4)
    void aCopyWithANewNameSkipsTheRowsItAlreadyHas() throws Exception {
        Files.copy(folder.resolve("batch2024.csv"), folder.resolve("copy-of-batch.csv"), StandardCopyOption.REPLACE_EXISTING);
        watcher.scan();
        watcher.scan();
        assertEquals(3, count("SELECT COUNT(*) FROM corpus_projects"));
        assertEquals(1, count("SELECT COUNT(*) FROM ingest_log WHERE file_name = 'copy-of-batch.csv' AND rows_added = 0 AND rows_skipped = 4"));
    }

    @Test @Order(5)
    void aChangedFileIsReadAgainAndOnlyNewRowsAreAdded() throws Exception {
        Files.writeString(folder.resolve("batch2024.csv"), CSV + "Blood Donor Map,2024,Finds nearby blood donors on a map,Java\n", StandardCharsets.UTF_8);
        watcher.scan();
        watcher.scan();
        assertEquals(4, count("SELECT COUNT(*) FROM corpus_projects"));
    }

    @Test @Order(6)
    void aBadHeaderRejectsTheWholeFileWithAReason() throws Exception {
        Files.writeString(folder.resolve("bad.csv"), "name of thing,when\nSomething,2022\n", StandardCharsets.UTF_8);
        watcher.scan();
        watcher.scan();
        assertEquals(4, count("SELECT COUNT(*) FROM corpus_projects"));
        assertEquals(1, count("SELECT COUNT(*) FROM ingest_log WHERE file_name = 'bad.csv' AND status = 'FAILED' AND note LIKE 'No title column%'"));
    }

    @Test @Order(7)
    void nonCsvFilesAreLoggedAsUnsupportedAndHiddenFilesAreIgnored() throws Exception {
        Files.write(folder.resolve("old-report.pdf"), new byte[] {'%', 'P', 'D', 'F'});
        Files.writeString(folder.resolve("old-report.txt"), "title|year\nOld Project|2022\n", StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("old-report.xlsx"), "not used", StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("old-report.docx"), "not used", StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("~$batch.csv"), "lock", StandardCharsets.UTF_8);
        watcher.scan();
        watcher.scan();
        assertEquals(1, count("SELECT COUNT(*) FROM ingest_log WHERE file_name = 'old-report.pdf' AND status = 'UNSUPPORTED'"));
        assertEquals(1, count("SELECT COUNT(*) FROM ingest_log WHERE file_name = 'old-report.txt' AND status = 'UNSUPPORTED'"));
        assertEquals(1, count("SELECT COUNT(*) FROM ingest_log WHERE file_name = 'old-report.xlsx' AND status = 'UNSUPPORTED'"));
        assertEquals(1, count("SELECT COUNT(*) FROM ingest_log WHERE file_name = 'old-report.docx' AND status = 'UNSUPPORTED'"));
        assertEquals(0, count("SELECT COUNT(*) FROM ingest_log WHERE file_name LIKE '~$%'"));
    }

    @Test @Order(8)
    void aFileStillGrowingIsNotReadUntilItStopsChanging() throws Exception {
        Path f = folder.resolve("growing.csv");
        Files.writeString(f, "title,year\nPartial Project One,2020\n", StandardCharsets.UTF_8);
        watcher.scan();
        Files.writeString(f, "title,year\nPartial Project One,2020\nPartial Project Two,2021\n", StandardCharsets.UTF_8);
        watcher.scan();                                   // size changed since the last scan: still not read
        assertEquals(0, count("SELECT COUNT(*) FROM corpus_projects WHERE title LIKE 'Partial Project%'"));
        watcher.scan();                                   // same as the last scan: read
        assertEquals(2, count("SELECT COUNT(*) FROM corpus_projects WHERE title LIKE 'Partial Project%'"));
    }

    @Test @Order(9)
    void aMissingFolderIsCreatedInsteadOfFailing() throws Exception {
        Path nothing = folder.resolve("not-yet-here");
        assertEquals(0, new DbFolderWatcher(nothing, service, log).scan());
        assertTrue(Files.isDirectory(nothing));
    }
}
