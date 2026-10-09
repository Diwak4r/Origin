package np.edu.origin.model;

import java.util.List;

/** One row on the result page: a past project or a class proposal close to the idea. */
public class MatchView {

    private int id;
    private String pool;
    private int targetId;
    private String title;
    private String abstractText;
    private String origin;
    private String domainName;
    private String typeName;
    private double tagScore;
    private double keywordScore;
    private double typeScore;
    private double ruleScore;
    private Double conceptScore;
    private double finalScore;
    private List<String> sharedTags = List.of();

    public int getId()                     { return id; }
    public void setId(int v)               { this.id = v; }
    public String getPool()                { return pool; }
    public void setPool(String v)          { this.pool = v; }
    public int getTargetId()               { return targetId; }
    public void setTargetId(int v)         { this.targetId = v; }
    public String getTitle()               { return title; }
    public void setTitle(String v)         { this.title = v; }
    public String getAbstractText()        { return abstractText; }
    public void setAbstractText(String v)  { this.abstractText = v; }

    /** Where the match comes from, for example "2021" or "Group G04". */
    public String getOrigin()              { return origin; }
    public void setOrigin(String v)        { this.origin = v; }
    public String getDomainName()          { return domainName; }
    public void setDomainName(String v)    { this.domainName = v; }
    public String getTypeName()            { return typeName; }
    public void setTypeName(String v)      { this.typeName = v; }
    public double getTagScore()            { return tagScore; }
    public void setTagScore(double v)      { this.tagScore = v; }
    public double getKeywordScore()        { return keywordScore; }
    public void setKeywordScore(double v)  { this.keywordScore = v; }
    public double getTypeScore()           { return typeScore; }
    public void setTypeScore(double v)     { this.typeScore = v; }
    public double getRuleScore()           { return ruleScore; }
    public void setRuleScore(double v)     { this.ruleScore = v; }
    public Double getConceptScore()        { return conceptScore; }
    public void setConceptScore(Double v)  { this.conceptScore = v; }
    public double getFinalScore()          { return finalScore; }
    public void setFinalScore(double v)    { this.finalScore = v; }
    public List<String> getSharedTags()    { return sharedTags; }
    public void setSharedTags(List<String> v) { this.sharedTags = v; }

    public int getPercent()         { return percent(finalScore); }
    public int getTagPercent()      { return percent(tagScore); }
    public int getKeywordPercent()  { return percent(keywordScore); }
    public int getConceptPercent()  { return conceptScore == null ? 0 : percent(conceptScore); }
    public boolean isSameType()     { return typeScore >= 1.0; }
    public boolean isConceptKnown() { return conceptScore != null; }

    /** Band name, used to colour the row. Same thresholds as the Band enum. */
    public String getBand() {
        if (finalScore >= np.edu.origin.engine.Band.DUPLICATE_FROM) return "DUPLICATE";
        if (finalScore >= np.edu.origin.engine.Band.SIMILAR_FROM) return "SIMILAR";
        return "ORIGINAL";
    }

    private static int percent(double v) {
        return (int) Math.round(v * 100);
    }
}
