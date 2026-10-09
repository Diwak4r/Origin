package np.edu.origin.web;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import np.edu.origin.App;

/** Starts Origin (schema, seed data, background jobs) when Tomcat deploys the app, and stops it cleanly. */
@WebListener
public class AppListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent event) {
        try {
            App.start();
        } catch (Exception e) {
            throw new IllegalStateException("Origin could not start. Is MySQL running and config/origin.properties "
                    + "correct? Cause: " + e.getMessage(), e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        App.stop();
    }
}
