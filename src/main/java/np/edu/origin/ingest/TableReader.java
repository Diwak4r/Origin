package np.edu.origin.ingest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Reads one file into a plain table: a list of rows, each a list of cell texts. The first row is
 * the header. Origin accepts CSV only.
 */
public interface TableReader {

    List<List<String>> read(Path file) throws IOException;

    /** The reader for a CSV file, or null for every other format (XLSX, DOCX, TXT, PDF, images). */
    static TableReader forFile(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv")) return new DelimitedReader();
        return null;
    }
}
