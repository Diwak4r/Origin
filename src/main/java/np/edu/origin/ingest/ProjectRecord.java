package np.edu.origin.ingest;

/**
 * One past project as read from a file, before domain, type and tags are resolved.
 * {@code line} is the row number in the file (the header is line 1) for error messages.
 */
public record ProjectRecord(int line, String title, int year, String abstractText, String tech,
                            String type, String domain, String tags) {
}
