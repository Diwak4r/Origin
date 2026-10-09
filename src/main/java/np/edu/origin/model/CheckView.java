package np.edu.origin.model;

import np.edu.origin.engine.Band;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Everything the result page shows for one idea check. */
public class CheckView {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

    private int id;
    private int userId;
    private String title;
    private String abstractText;
    private String problem;
    private int domainId;
    private String domainName;
    private int typeId;
    private String typeName;
    private int targetSemester;
    private List<String> tags = new ArrayList<>();
    private String verdict;
    private double ruleScore;
    private double finalScore;
    private boolean claimFlag;
    private String engineStatus;
    private String fingerprint;
    private LocalDateTime createdAt;
    private Integer proposalId;
    private final List<MatchView> history = new ArrayList<>();
    private final List<MatchView> classMatches = new ArrayList<>();
    private final List<String> claimReasons = new ArrayList<>();

    public int getId()                        { return id; }
    public void setId(int v)                  { this.id = v; }
    public int getUserId()                    { return userId; }
    public void setUserId(int v)              { this.userId = v; }
    public String getTitle()                  { return title; }
    public void setTitle(String v)            { this.title = v; }
    public String getAbstractText()           { return abstractText; }
    public void setAbstractText(String v)     { this.abstractText = v; }
    public String getProblem()                { return problem; }
    public void setProblem(String v)          { this.problem = v; }
    public int getDomainId()                  { return domainId; }
    public void setDomainId(int v)            { this.domainId = v; }
    public String getDomainName()             { return domainName; }
    public void setDomainName(String v)       { this.domainName = v; }
    public int getTypeId()                    { return typeId; }
    public void setTypeId(int v)              { this.typeId = v; }
    public String getTypeName()               { return typeName; }
    public void setTypeName(String v)         { this.typeName = v; }
    public int getTargetSemester()            { return targetSemester; }
    public void setTargetSemester(int v)      { this.targetSemester = v; }
    public List<String> getTags()             { return tags; }
    public void setTags(List<String> v)       { this.tags = v; }
    public String getVerdict()                { return verdict; }
    public void setVerdict(String v)          { this.verdict = v; }
    public double getRuleScore()              { return ruleScore; }
    public void setRuleScore(double v)        { this.ruleScore = v; }
    public double getFinalScore()             { return finalScore; }
    public void setFinalScore(double v)       { this.finalScore = v; }
    public boolean isClaimFlag()              { return claimFlag; }
    public void setClaimFlag(boolean v)       { this.claimFlag = v; }
    public String getEngineStatus()           { return engineStatus; }
    public void setEngineStatus(String v)     { this.engineStatus = v; }
    public String getFingerprint()            { return fingerprint; }
    public void setFingerprint(String v)      { this.fingerprint = v; }
    public LocalDateTime getCreatedAt()       { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public Integer getProposalId()            { return proposalId; }
    public void setProposalId(Integer v)      { this.proposalId = v; }
    public List<MatchView> getHistory()       { return history; }
    public List<MatchView> getClassMatches()  { return classMatches; }
    public List<String> getClaimReasons()     { return claimReasons; }

    public int getPercent()        { return (int) Math.round(finalScore * 100); }
    public boolean isPending()     { return "PENDING".equals(engineStatus); }
    public boolean isLocked()      { return proposalId != null; }
    public String getVerdictLabel()  { return Band.valueOf(verdict).label(); }
    public String getVerdictAdvice() { return Band.valueOf(verdict).advice(); }
    public String getCreatedText()   { return createdAt == null ? "" : createdAt.format(WHEN); }

    /** The meaning column appears only while the background comparison runs or after it has answered. */
    public boolean isMeaningShown() {
        if (isPending()) return true;
        for (MatchView m : history) if (m.isConceptKnown()) return true;
        return false;
    }

    /** The single closest match across both pools, or null when there is none. */
    public MatchView getClosest() {
        MatchView best = null;
        for (MatchView m : history) if (best == null || m.getFinalScore() > best.getFinalScore()) best = m;
        for (MatchView m : classMatches) if (best == null || m.getFinalScore() > best.getFinalScore()) best = m;
        return best;
    }
}
