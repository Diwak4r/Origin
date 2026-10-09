package np.edu.origin.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** A group's locked idea, as shown on the Class Radar and on the supervisor's review screen. */
public class ProposalView {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM, HH:mm");

    private int id;
    private String groupCode;
    private String status;
    private LocalDateTime lockedAt;
    private int checkId;
    private String title;
    private String abstractText;
    private String verdict;
    private double finalScore;
    private String domainName;
    private String typeName;
    private String lockedByName;
    private final List<String> memberNames = new ArrayList<>();
    private int collisions;
    private final List<String> collidesWith = new ArrayList<>();

    public int getId()                        { return id; }
    public void setId(int v)                  { this.id = v; }
    public String getGroupCode()              { return groupCode; }
    public void setGroupCode(String v)        { this.groupCode = v; }
    public String getStatus()                 { return status; }
    public void setStatus(String v)           { this.status = v; }
    public LocalDateTime getLockedAt()        { return lockedAt; }
    public void setLockedAt(LocalDateTime v)  { this.lockedAt = v; }
    public int getCheckId()                   { return checkId; }
    public void setCheckId(int v)             { this.checkId = v; }
    public String getTitle()                  { return title; }
    public void setTitle(String v)            { this.title = v; }
    public String getAbstractText()           { return abstractText; }
    public void setAbstractText(String v)     { this.abstractText = v; }
    public String getVerdict()                { return verdict; }
    public void setVerdict(String v)          { this.verdict = v; }
    public double getFinalScore()             { return finalScore; }
    public void setFinalScore(double v)       { this.finalScore = v; }
    public String getDomainName()             { return domainName; }
    public void setDomainName(String v)       { this.domainName = v; }
    public String getTypeName()               { return typeName; }
    public void setTypeName(String v)         { this.typeName = v; }
    public String getLockedByName()           { return lockedByName; }
    public void setLockedByName(String v)     { this.lockedByName = v; }

    /** Full names of every student account in the group. Names only: no emails and no ids. */
    public List<String> getMemberNames()      { return memberNames; }
    public int getCollisions()                { return collisions; }
    public void setCollisions(int v)          { this.collisions = v; }

    /** "G06 (71%)" entries for every group this proposal clashes with. */
    public List<String> getCollidesWith()     { return collidesWith; }

    public int getPercent()       { return (int) Math.round(finalScore * 100); }
    public String getLockedText() { return lockedAt == null ? "" : lockedAt.format(WHEN); }
}
