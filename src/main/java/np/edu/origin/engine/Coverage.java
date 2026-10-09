package np.edu.origin.engine;

/**
 * How much of the idea the closest few projects cover together.
 * A mash-up of two old projects can score low against each one alone and still be
 * entirely made of old parts; this is the number that shows it.
 *
 * @param share    weighted share of the idea's terms found in the union of those projects, 0..1
 * @param projects how many of the closest projects contributed at least one term
 */
public record Coverage(double share, int projects) {

    public int getPercent()      { return percent(); }
    public String getSentence()  { return sentence(); }

    public int percent() {
        return (int) Math.round(share * 100);
    }

    /** "Your idea is 82% covered by 2 projects together." Empty when nothing is covered. */
    public String sentence() {
        if (projects == 0 || percent() == 0) return "";
        return "Your idea is " + percent() + "% covered by " + projects
                + (projects == 1 ? " project." : " projects together.");
    }
}
