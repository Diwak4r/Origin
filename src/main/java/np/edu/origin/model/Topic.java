package np.edu.origin.model;

import java.util.List;

/** One topic of the BIT 4th semester syllabus, with the words that show a project uses it. */
public class Topic {

    private final int id;
    private final String course;
    private final int unitNo;
    private final String name;
    private final boolean crud;
    private final List<String> keywords;

    public Topic(int id, String course, int unitNo, String name, boolean crud, List<String> keywords) {
        this.id = id;
        this.course = course;
        this.unitNo = unitNo;
        this.name = name;
        this.crud = crud;
        this.keywords = keywords;
    }

    public int getId()              { return id; }
    public String getCourse()       { return course; }
    public int getUnitNo()          { return unitNo; }
    public String getName()         { return name; }
    /** True for everyday CRUD plumbing (JDBC, Servlet and JSP, ER design): it does not make a project stand out. */
    public boolean isCrud()         { return crud; }
    public List<String> getKeywords() { return keywords; }

    /** Course and unit as one key, such as "OS-6", used to count distinct syllabus units. */
    public String getUnitKey()      { return course + "-" + unitNo; }
}
