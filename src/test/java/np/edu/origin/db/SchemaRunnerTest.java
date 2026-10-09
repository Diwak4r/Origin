package np.edu.origin.db;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaRunnerTest {

    @Test
    void splitsOnSemicolonsAndHonoursDelimiterBlocks() {
        String script = """
                -- a comment
                CREATE TABLE a (id INT);
                DELIMITER $$
                CREATE TRIGGER t AFTER INSERT ON a FOR EACH ROW
                BEGIN
                    UPDATE a SET id = id;
                END$$
                DELIMITER ;
                DROP VIEW IF EXISTS v;
                """;
        List<String> s = SchemaRunner.split(script);
        assertEquals(3, s.size(), s.toString());
        assertTrue(s.get(1).startsWith("CREATE TRIGGER"));
        assertTrue(s.get(1).contains("UPDATE a SET id = id;"), "semicolons inside a trigger body stay inside it");
        assertTrue(s.get(1).endsWith("END"));
    }

    @Test
    void theRealSchemaSplitsIntoTheExpectedObjects() throws Exception {
        List<String> s = SchemaRunner.split(SchemaRunner.read("db/schema.sql"));
        long tables = s.stream().filter(x -> x.startsWith("CREATE TABLE")).count();
        long triggers = s.stream().filter(x -> x.startsWith("CREATE TRIGGER")).count();
        long views = s.stream().filter(x -> x.startsWith("CREATE OR REPLACE VIEW")).count();
        long procs = s.stream().filter(x -> x.startsWith("CREATE PROCEDURE")).count();
        assertEquals(25, tables);   // 19 from v2 + ingest_log + 5 syllabus tables
        assertEquals(6, triggers);
        assertEquals(4, views);
        assertEquals(2, procs);
    }
}
