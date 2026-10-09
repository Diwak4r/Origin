package np.edu.origin.ingest;

import np.edu.origin.dao.IngestDao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Looks in the DB folder on a timer and hands every new or changed file to the {@link IngestService}.
 * A file is read only when its size and time are the same on two scans in a row, so a file that is
 * still being copied is never half-read. A file whose size or time changed is read again.
 */
public class DbFolderWatcher {

    private final Path folder;
    private final IngestService service;
    private final IngestDao log;
    private final Map<String, String> lastSeen = new HashMap<>();
    private final Set<String> handled = new HashSet<>();

    public DbFolderWatcher(Path folder, IngestService service, IngestDao log) {
        this.folder = folder;
        this.service = service;
        this.log = log;
    }

    public Path folder() {
        return folder;
    }

    /** One pass over the folder. Returns how many files were ingested in this pass. */
    public synchronized int scan() throws IOException {
        Files.createDirectories(folder);
        int ingested = 0;
        List<Path> files;
        try (Stream<Path> s = Files.list(folder)) {
            files = s.filter(Files::isRegularFile).sorted().toList();
        }
        for (Path file : files) {
            String name = file.getFileName().toString();
            if (name.startsWith(".") || name.startsWith("~$")) continue;       // hidden and Office lock files
            try {
                String version = Files.size(file) + ":" + Files.getLastModifiedTime(file).toMillis();
                boolean stable = version.equals(lastSeen.put(name, version));
                if (!stable || handled.contains(name + "|" + version)) continue;
                long size = Files.size(file);
                long modified = Files.getLastModifiedTime(file).toMillis();
                if (!log.seen(name, size, modified)) {
                    service.ingest(file);
                    ingested++;
                }
                handled.add(name + "|" + version);
            } catch (SQLException | IOException e) {
                // The file stays unhandled and is tried again on the next scan.
                System.err.println("[origin] ingest of " + name + " failed: " + e.getMessage());
            }
        }
        return ingested;
    }

    /** For the scheduler: a failed pass is logged and the schedule keeps running. */
    public void scanQuietly() {
        try {
            int n = scan();
            if (n > 0) System.out.println("[origin] ingest: " + n + " file(s) read from " + folder);
        } catch (IOException | RuntimeException e) {
            System.err.println("[origin] DB folder scan failed: " + e);
        }
    }
}
