package np.edu.origin.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Reads settings from config/origin.properties (next to the project) and lets
 * environment variables override them. Secrets such as the decision-engine key
 * live only in that file or in the environment, never in source code.
 */
public final class AppConfig {

    private static final Properties PROPS = load();

    private AppConfig() { }

    private static Properties load() {
        Properties p = new Properties();
        Path file = Path.of(System.getProperty("origin.config", "config/origin.properties"));
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            } catch (IOException e) {
                System.err.println("[origin] could not read " + file.toAbsolutePath() + ": " + e.getMessage());
            }
        }
        return p;
    }

    /** Environment variable (ORIGIN_DB_URL style) first, then the properties file, then the default. */
    public static String get(String key, String defaultValue) {
        String env = System.getenv(key.toUpperCase().replace('.', '_'));
        if (env != null && !env.isBlank()) return env.trim();
        String v = PROPS.getProperty(key);
        return (v == null || v.isBlank()) ? defaultValue : v.trim();
    }

    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static String dbUrl() {
        return get("origin.db.url",
                "jdbc:mysql://localhost:3306/origin?createDatabaseIfNotExist=true&serverTimezone=Asia/Kathmandu&allowMultiQueries=false");
    }

    /** The folder the app watches for past-project CSVs. */
    public static String dbFolder()   { return get("origin.db.folder", "DB"); }

    public static String dbUser()     { return get("origin.db.user", "root"); }
    public static String dbPassword() { return get("origin.db.password", ""); }

    /** Key for the decision engine. TYPESAFE_API_KEY is also accepted as-is. */
    public static String engineKey() {
        String direct = System.getenv("TYPESAFE_API_KEY");
        if (direct != null && !direct.isBlank()) return direct.trim();
        return get("origin.engine.key", "");
    }

    public static String engineUrl()   { return get("origin.engine.url", "https://api.typesafe.ai/v1/systemone"); }
    public static String engineModel() { return get("origin.engine.model", "jev-latest"); }
}
