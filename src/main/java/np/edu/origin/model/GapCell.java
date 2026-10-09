package np.edu.origin.model;

/** One square of the gap map: a domain crossed with a project type. */
public class GapCell {

    private final int domainId;
    private final String domainName;
    private final String shortName;
    private final int typeId;
    private final String typeName;
    private final int projectCount;
    private final int liveCount;
    private final int openIdeas;

    public GapCell(int domainId, String domainName, String shortName, int typeId, String typeName,
                   int projectCount, int liveCount, int openIdeas) {
        this.domainId = domainId;
        this.domainName = domainName;
        this.shortName = shortName;
        this.typeId = typeId;
        this.typeName = typeName;
        this.projectCount = projectCount;
        this.liveCount = liveCount;
        this.openIdeas = openIdeas;
    }

    public int getDomainId()      { return domainId; }
    public String getDomainName() { return domainName; }
    public String getShortName()  { return shortName; }
    public int getTypeId()        { return typeId; }
    public String getTypeName()   { return typeName; }
    public int getProjectCount()  { return projectCount; }
    public int getLiveCount()     { return liveCount; }
    public int getOpenIdeas()     { return openIdeas; }

    /** 0 untouched, 1 thin, 2 worked, 3 crowded. The page colours the cell by this level. */
    public int getLevel() {
        int n = projectCount + liveCount;
        if (n == 0) return 0;
        if (n == 1) return 1;
        if (n <= 3) return 2;
        return 3;
    }

    public boolean isOpen() {
        return projectCount + liveCount == 0;
    }
}
