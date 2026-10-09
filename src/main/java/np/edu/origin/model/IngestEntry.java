package np.edu.origin.model;

import java.time.LocalDateTime;

/** One line of the ingest log, as the supervisor page shows it. */
public class IngestEntry {

    private final String fileName;
    private final String status;
    private final int rowsAdded;
    private final int rowsSkipped;
    private final String note;
    private final LocalDateTime processedAt;

    public IngestEntry(String fileName, String status, int rowsAdded, int rowsSkipped, String note,
                       LocalDateTime processedAt) {
        this.fileName = fileName;
        this.status = status;
        this.rowsAdded = rowsAdded;
        this.rowsSkipped = rowsSkipped;
        this.note = note;
        this.processedAt = processedAt;
    }

    public String getFileName()           { return fileName; }
    public String getStatus()             { return status; }
    public int getRowsAdded()             { return rowsAdded; }
    public int getRowsSkipped()           { return rowsSkipped; }
    public String getNote()               { return note; }
    public LocalDateTime getProcessedAt() { return processedAt; }
}
