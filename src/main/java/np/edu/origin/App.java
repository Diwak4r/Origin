package np.edu.origin;

import np.edu.origin.dao.AdminDao;
import np.edu.origin.dao.CacheDao;
import np.edu.origin.dao.CheckDao;
import np.edu.origin.dao.CorpusDao;
import np.edu.origin.dao.GapDao;
import np.edu.origin.dao.IdeaDao;
import np.edu.origin.dao.IngestDao;
import np.edu.origin.dao.LookupDao;
import np.edu.origin.dao.ProposalDao;
import np.edu.origin.dao.SyllabusDao;
import np.edu.origin.dao.UserDao;
import np.edu.origin.db.Seeder;
import np.edu.origin.db.SyllabusSeeder;
import np.edu.origin.decision.DecisionClient;
import np.edu.origin.ingest.DbFolderWatcher;
import np.edu.origin.ingest.IngestService;
import np.edu.origin.config.AppConfig;
import np.edu.origin.service.AuthService;
import np.edu.origin.service.CheckService;
import np.edu.origin.service.GapService;
import np.edu.origin.service.IdeaService;
import np.edu.origin.service.RadarService;
import np.edu.origin.service.SyllabusService;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds every DAO and service once and owns the two background thread pools:
 * "workers" for the decision engine calls, and "maintenance" that refreshes the
 * idea suggestions and retries unfinished checks every ten minutes.
 */
public final class App {

    private static volatile App instance;

    public final LookupDao lookups = new LookupDao();
    public final CorpusDao corpus = new CorpusDao();
    public final ProposalDao proposals = new ProposalDao();
    public final CheckDao checkDao = new CheckDao();
    public final IdeaDao ideaDao = new IdeaDao();
    public final GapDao gapDao = new GapDao();
    public final UserDao users = new UserDao();
    public final AdminDao admin = new AdminDao();
    public final CacheDao cache = new CacheDao();
    public final IngestDao ingestLog = new IngestDao();
    public final SyllabusDao syllabusDao = new SyllabusDao();

    // Jev (the decision engine) is switched off for now: the app runs on rules alone and never calls it.
    // To switch it back on, replace the line below with:
    //     public final DecisionClient decisions = DecisionClient.fromConfig(cache);
    // and uncomment the Jev block in IdeaService.refresh().
    public final DecisionClient decisions = DecisionClient.disabled();
    private final ExecutorService workers = Executors.newFixedThreadPool(2, named("origin-worker"));
    private final ScheduledExecutorService maintenance = Executors.newSingleThreadScheduledExecutor(named("origin-maintenance"));
    private final ScheduledExecutorService folderScan = Executors.newSingleThreadScheduledExecutor(named("origin-ingest"));

    public final AuthService auth = new AuthService(users);
    public final CheckService checks = new CheckService(lookups, corpus, proposals, checkDao, admin, decisions, workers);
    public final SyllabusService syllabus = new SyllabusService(syllabusDao);
    public final IdeaService ideas = new IdeaService(lookups, corpus, proposals, ideaDao, gapDao, syllabus, syllabusDao, decisions, workers);
    public final RadarService radar = new RadarService(lookups, corpus, proposals, checkDao, checks, this::refreshIdeasLater);
    public final GapService gaps = new GapService(gapDao, corpus, proposals, ideaDao);
    public final IngestService ingest = new IngestService(lookups, corpus, ingestLog, this::refreshIdeasLater);
    public final DbFolderWatcher watcher = new DbFolderWatcher(Path.of(AppConfig.dbFolder()), ingest, ingestLog);

    private App() { }

    public static App get() {
        App a = instance;
        if (a == null) throw new IllegalStateException("Origin has not started");
        return a;
    }

    /** Creates the schema, seeds an empty database and starts the background jobs. */
    public static synchronized App start() throws IOException, SQLException {
        if (instance != null) return instance;
        App app = new App();
        new Seeder(app.lookups, app.corpus, app.ideaDao, app.users, app.checks, app.radar).run();
        new SyllabusSeeder(app.syllabusDao, app.corpus).run();
        app.syllabus.linkNewProjects();
        app.maintenance.scheduleWithFixedDelay(app::maintain, 2, 600, TimeUnit.SECONDS);
        // Every 5 seconds: a file is read after it has been unchanged for one scan, so a drop shows up within about 10.
        app.folderScan.scheduleWithFixedDelay(app.watcher::scanQuietly, 1, 5, TimeUnit.SECONDS);
        instance = app;
        System.out.println("[origin] ready. Decision engine off. Watching " + app.watcher.folder().toAbsolutePath().normalize() + " for past projects.");
        return app;
    }

    public static synchronized void stop() {
        if (instance == null) return;
        instance.maintenance.shutdownNow();
        instance.folderScan.shutdownNow();
        instance.workers.shutdownNow();
        instance = null;
    }

    /** Runs on the maintenance thread. */
    void maintain() {
        try {
            int n = ideas.refresh();
            System.out.println("[origin] maintenance: " + n + " suggestions refreshed");
            if (decisions.isEnabled()) {
                for (int id : checkDao.unfinished(20)) checks.refineQuietly(id);
            }
        } catch (SQLException | RuntimeException e) {
            // Log and keep the schedule alive; an exception here would silently cancel future runs.
            System.err.println("[origin] maintenance failed: " + e);
        }
    }

    /** Called after a group locks an idea: suggestions that group just took are marked TAKEN. */
    public void refreshIdeasLater() {
        workers.execute(() -> {
            try {
                ideas.refresh();
            } catch (SQLException e) {
                System.err.println("[origin] idea refresh failed: " + e.getMessage());
            }
        });
    }

    private static ThreadFactory named(String prefix) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
