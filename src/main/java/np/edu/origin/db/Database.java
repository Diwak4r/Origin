package np.edu.origin.db;

import np.edu.origin.config.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * The single place that opens JDBC connections. Every DAO calls {@link #connect()}
 * inside try-with-resources, so connections are always closed.
 */
public final class Database {

    private static volatile String url = AppConfig.dbUrl();
    private static volatile String user = AppConfig.dbUser();
    private static volatile String password = AppConfig.dbPassword();

    private Database() { }

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /** Used by tests to point the whole application at a separate database. */
    public static void configure(String newUrl, String newUser, String newPassword) {
        url = newUrl;
        user = newUser;
        password = newPassword;
    }
}
