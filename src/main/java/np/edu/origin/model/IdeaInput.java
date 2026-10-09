package np.edu.origin.model;

/** What a student types into the check form, with extra spaces removed. Validation happens in the service. */
public class IdeaInput {

    private final String title;
    private final String abstractText;
    private final String problem;
    private final String tags;
    private final int domainId;
    private final int typeId;
    /** The program semester (1 to 8) the student builds this project in. Context only, not a scoring input. */
    private final int targetSemester;

    public IdeaInput(String title, String abstractText, String problem, String tags, int domainId, int typeId,
                     int targetSemester) {
        this.title = clean(title);
        this.abstractText = clean(abstractText);
        this.problem = clean(problem);
        this.tags = clean(tags);
        this.domainId = domainId;
        this.typeId = typeId;
        this.targetSemester = targetSemester;
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    public String getTitle()        { return title; }
    public String getAbstractText() { return abstractText; }
    public String getProblem()      { return problem; }
    public String getTags()         { return tags; }
    public int getDomainId()        { return domainId; }
    public int getTypeId()          { return typeId; }
    public int getTargetSemester()  { return targetSemester; }
}
