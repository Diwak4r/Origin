package np.edu.origin.engine;

import java.util.List;

/**
 * A match told as a story: what the two ideas share, what only yours has, and what only theirs has.
 * Each list is ordered rarest term first, so the first items are the ones that matter most.
 */
public record Story(List<String> shared, List<String> yoursOnly, List<String> theirsOnly) {

    // JSP expression language reads getters, not record accessors.
    public List<String> getShared()     { return shared; }
    public List<String> getYoursOnly()  { return yoursOnly; }
    public List<String> getTheirsOnly() { return theirsOnly; }

    /** One sentence that tells the student what to do next. */
    public String fix(String otherTitle, int percent) {
        String head = "You match " + otherTitle + " (" + percent + "%)";
        if (shared.isEmpty()) return head + ", but on no shared topic. Nothing to change.";
        String on = " on " + join(shared, 3);
        if (yoursOnly.isEmpty()) {
            return head + on + " and add nothing it lacks. Choose a different problem, user group or method.";
        }
        return head + on + ". Your real difference is " + join(yoursOnly, 2) + ". Make that the core of the project.";
    }

    private static String join(List<String> terms, int max) {
        List<String> top = terms.subList(0, Math.min(max, terms.size()));
        return String.join(", ", top);
    }
}
