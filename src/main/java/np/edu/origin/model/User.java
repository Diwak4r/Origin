package np.edu.origin.model;

import java.io.Serializable;

/** A logged-in person. It lives in the HTTP session, so it carries no password data. */
public class User implements Serializable {

    private final int id;
    private final String fullName;
    private final String email;
    private final String role;
    private final String groupCode;

    public User(int id, String fullName, String email, String role, String groupCode) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.groupCode = groupCode;
    }

    public int getId()            { return id; }
    public String getFullName()   { return fullName; }
    public String getEmail()      { return email; }
    public String getRole()       { return role; }
    public String getGroupCode()  { return groupCode; }
    public boolean isSupervisor() { return "SUPERVISOR".equals(role); }

    public String getFirstName() {
        int space = fullName.indexOf(' ');
        return space > 0 ? fullName.substring(0, space) : fullName;
    }
}
