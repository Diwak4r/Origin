package np.edu.origin.engine;

/**
 * The verdict a student sees. Thresholds match the SQL function fn_band so the
 * database and the Java code always agree.
 */
public enum Band {
    ORIGINAL("Looks original", "Nothing close enough in the corpus or the class. Go ahead."),
    SIMILAR("Overlaps", "Parts of this idea exist already. Change the problem, the users or the approach."),
    DUPLICATE("Already done", "This is very close to an existing project. Rework it before you submit."),
    LOW_CONFIDENCE("Not enough data", "The corpus is too small to judge. Treat the score as a hint.");

    public static final double SIMILAR_FROM = 0.35;
    public static final double DUPLICATE_FROM = 0.60;
    public static final int MIN_CORPUS = 15;

    private final String label;
    private final String advice;

    Band(String label, String advice) {
        this.label = label;
        this.advice = advice;
    }

    public String label()  { return label; }
    public String advice() { return advice; }

    public static Band of(double score, int corpusSize) {
        if (corpusSize < MIN_CORPUS) return LOW_CONFIDENCE;
        if (score >= DUPLICATE_FROM) return DUPLICATE;
        if (score >= SIMILAR_FROM) return SIMILAR;
        return ORIGINAL;
    }
}
