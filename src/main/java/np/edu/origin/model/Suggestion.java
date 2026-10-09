package np.edu.origin.model;

import java.util.List;

/** A ready-to-pick project idea: a real problem paired with a project type that few groups have tried. */
public class Suggestion {

    private int id;
    private int problemId;
    private String title;
    private String statement;
    private String affected;
    private String source;
    private int domainId;
    private String domainName;
    private int typeId;
    private String typeName;
    private int gapCount;
    private double nearestScore;
    private String nearestTitle;
    private double novelty;
    private double feasibility;
    private double impact;
    private int difficulty;
    private double strength;
    private String status;
    private String takenByGroup;
    private List<String> tags = List.of();
    private String twist;
    private String pairLabel;
    private int pairUses;
    private int totalProjects;
    private int units;
    private int courses;
    private boolean crudOnly;
    private List<String> topics = List.of();

    public int getId()                    { return id; }
    public void setId(int v)              { this.id = v; }
    public int getProblemId()             { return problemId; }
    public void setProblemId(int v)       { this.problemId = v; }
    public String getTitle()              { return title; }
    public void setTitle(String v)        { this.title = v; }
    public String getStatement()          { return statement; }
    public void setStatement(String v)    { this.statement = v; }
    public String getAffected()           { return affected; }
    public void setAffected(String v)     { this.affected = v; }
    public String getSource()             { return source; }
    public void setSource(String v)       { this.source = v; }
    public int getDomainId()              { return domainId; }
    public void setDomainId(int v)        { this.domainId = v; }
    public String getDomainName()         { return domainName; }
    public void setDomainName(String v)   { this.domainName = v; }
    public int getTypeId()                { return typeId; }
    public void setTypeId(int v)          { this.typeId = v; }
    public String getTypeName()           { return typeName; }
    public void setTypeName(String v)     { this.typeName = v; }
    public int getGapCount()              { return gapCount; }
    public void setGapCount(int v)        { this.gapCount = v; }
    public double getNearestScore()       { return nearestScore; }
    public void setNearestScore(double v) { this.nearestScore = v; }
    public String getNearestTitle()       { return nearestTitle; }
    public void setNearestTitle(String v) { this.nearestTitle = v; }
    public double getNovelty()            { return novelty; }
    public void setNovelty(double v)      { this.novelty = v; }
    public double getFeasibility()        { return feasibility; }
    public void setFeasibility(double v)  { this.feasibility = v; }
    public double getImpact()             { return impact; }
    public void setImpact(double v)       { this.impact = v; }
    public int getDifficulty()            { return difficulty; }
    public void setDifficulty(int v)      { this.difficulty = v; }
    public double getStrength()           { return strength; }
    public void setStrength(double v)     { this.strength = v; }
    public String getStatus()             { return status; }
    public void setStatus(String v)       { this.status = v; }
    public String getTakenByGroup()       { return takenByGroup; }
    public void setTakenByGroup(String v) { this.takenByGroup = v; }
    public List<String> getTags()         { return tags; }
    public void setTags(List<String> v)   { this.tags = v; }

    public String getTwist()              { return twist; }
    public void setTwist(String v)        { this.twist = v; }
    public String getPairLabel()          { return pairLabel; }
    public void setPairLabel(String v)    { this.pairLabel = v; }
    public int getPairUses()              { return pairUses; }
    public void setPairUses(int v)        { this.pairUses = v; }
    public int getTotalProjects()         { return totalProjects; }
    public void setTotalProjects(int v)   { this.totalProjects = v; }
    public int getUnits()                 { return units; }
    public void setUnits(int v)           { this.units = v; }
    public int getCourses()               { return courses; }
    public void setCourses(int v)         { this.courses = v; }
    public boolean isCrudOnly()           { return crudOnly; }
    public void setCrudOnly(boolean v)    { this.crudOnly = v; }
    public List<String> getTopics()       { return topics; }
    public void setTopics(List<String> v) { this.topics = v; }

    /** The proof: how many past projects already combined this suggestion's busiest topic pair. */
    public String getProofLine() {
        if (pairLabel == null) return "No syllabus topic is attached to this idea yet.";
        return pairUses + " of " + totalProjects + " past projects used " + pairLabel
                + (pairLabel.contains(" + ") ? " together." : ".");
    }

    public String getUnitsLine() {
        if (units == 0) return "Plain CRUD: nothing beyond tables and forms.";
        return "Covers " + units + (units == 1 ? " syllabus unit" : " syllabus units") + " across " + courses
                + (courses == 1 ? " course." : " courses.");
    }

    public int getStrengthPercent()    { return percent(strength); }
    public int getNoveltyPercent()     { return percent(novelty); }
    public int getFeasibilityPercent() { return percent(feasibility); }
    public int getImpactPercent()      { return percent(impact); }
    public int getNearestPercent()     { return percent(nearestScore); }
    public boolean isOpen()            { return "OPEN".equals(status); }

    public String getDifficultyLabel() {
        return switch (difficulty) {
            case 1 -> "Easy";
            case 2 -> "Moderate";
            case 3 -> "Challenging";
            default -> "Hard";
        };
    }

    /** Tags joined with commas, ready to pre-fill the check form. */
    public String getTagText() {
        return String.join(", ", tags);
    }

    private static int percent(double v) {
        return (int) Math.round(v * 100);
    }
}
