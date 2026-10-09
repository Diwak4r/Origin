package np.edu.origin.model;

import java.util.List;

/** A past project from the corpus, as listed on the gap map. */
public class ProjectRow {

    private final int id;
    private final String title;
    private final String abstractText;
    private final int year;
    private final String source;
    private final List<String> tags;

    public ProjectRow(int id, String title, String abstractText, int year, String source, List<String> tags) {
        this.id = id;
        this.title = title;
        this.abstractText = abstractText;
        this.year = year;
        this.source = source;
        this.tags = tags;
    }

    public int getId()              { return id; }
    public String getTitle()        { return title; }
    public String getAbstractText() { return abstractText; }
    public int getYear()            { return year; }
    public String getSource()       { return source; }
    public List<String> getTags()   { return tags; }
}
