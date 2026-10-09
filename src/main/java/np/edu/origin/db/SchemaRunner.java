package np.edu.origin.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Executes a .sql script from the classpath. Understands the MySQL client's
 * DELIMITER command so the same file works in Workbench and from Java.
 */
public final class SchemaRunner {

    private SchemaRunner() { }

    public static void run(Connection con, String resource) throws IOException, SQLException {
        List<String> statements = split(read(resource));
        try (Statement st = con.createStatement()) {
            for (String sql : statements) {
                st.execute(sql);
            }
        }
    }

    static String read(String resource) throws IOException {
        InputStream in = SchemaRunner.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) throw new IOException("Missing resource " + resource);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }

    /** Splits a script into statements, honouring "DELIMITER $$" blocks and skipping comments. */
    static List<String> split(String script) {
        List<String> out = new ArrayList<>();
        String delimiter = ";";
        StringBuilder current = new StringBuilder();
        for (String raw : script.split("\n")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("--")) continue;
            if (line.toUpperCase().startsWith("DELIMITER ")) {
                delimiter = line.substring(10).strip();
                continue;
            }
            current.append(raw).append('\n');
            if (line.endsWith(delimiter)) {
                String sql = current.toString().strip();
                sql = sql.substring(0, sql.length() - delimiter.length()).strip();
                if (!sql.isEmpty()) out.add(sql);
                current.setLength(0);
            }
        }
        if (!current.toString().isBlank()) out.add(current.toString().strip());
        return out;
    }
}
