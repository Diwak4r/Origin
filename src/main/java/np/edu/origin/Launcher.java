package np.edu.origin;

import np.edu.origin.config.AppConfig;
import org.apache.catalina.Context;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;

import java.io.File;

/**
 * Starts an embedded Apache Tomcat with the webapp folder and the compiled classes,
 * so the project runs with one command during development and in the demo.
 * Deploying target/origin.war to a standalone Tomcat 10.1 works the same way.
 */
public final class Launcher {

    private Launcher() { }

    public static void main(String[] args) throws Exception {
        int port = AppConfig.getInt("origin.port", 8080);
        File webapp = new File("src/main/webapp").getAbsoluteFile();
        File classes = new File("target/classes").getAbsoluteFile();
        if (!webapp.isDirectory()) {
            throw new IllegalStateException("Run the launcher from the Origin_Final folder (missing " + webapp + ")");
        }

        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.setBaseDir(new File("target/tomcat").getAbsolutePath());
        tomcat.getConnector();

        Context ctx = tomcat.addWebapp("", webapp.getAbsolutePath());
        WebResourceRoot resources = new StandardRoot(ctx);
        resources.addPreResources(new DirResourceSet(resources, "/WEB-INF/classes", classes.getAbsolutePath(), "/"));
        ctx.setResources(resources);

        tomcat.start();
        System.out.println("[origin] open http://localhost:" + port);
        tomcat.getServer().await();
    }
}
