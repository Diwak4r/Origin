package np.edu.origin.model;

/** A row from a small reference table: a domain or a project type. */
public class Lookup {

    private final int id;
    private final String name;
    private final String code;

    public Lookup(int id, String name, String code) {
        this.id = id;
        this.name = name;
        this.code = code;
    }

    public int getId()      { return id; }
    public String getName() { return name; }

    /** Short code for a domain (EDU), or the build effort (1 to 5) for a project type. */
    public String getCode() { return code; }
}
