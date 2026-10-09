package np.edu.origin.service;

import np.edu.origin.dao.UserDao;
import np.edu.origin.model.User;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Registration and login. Only students can register; supervisor accounts are created by the seeder. */
public class AuthService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[a-z]{2,}$");
    private static final Pattern GROUP = Pattern.compile("^G\\d{2}$");
    private static final String DUMMY_SALT = PasswordHasher.newSalt();

    private final UserDao users;

    public AuthService(UserDao users) {
        this.users = users;
    }

    /** Returns the user when the email and password match, otherwise null. The message never says which one was wrong. */
    public User login(String email, String password) throws SQLException {
        if (email == null || password == null) return null;
        UserDao.Stored stored = users.findByEmail(email.trim());
        if (stored == null) {
            PasswordHasher.hash(password, DUMMY_SALT);   // same work either way, so timing does not reveal unknown emails
            return null;
        }
        return PasswordHasher.matches(password, stored.salt(), stored.hash()) ? stored.user() : null;
    }

    public User register(String fullName, String email, String password, String groupCode)
            throws ValidationException, SQLException {
        String name = fullName == null ? "" : fullName.trim().replaceAll("\\s+", " ");
        String mail = email == null ? "" : email.trim().toLowerCase();
        String group = groupCode == null ? "" : groupCode.trim().toUpperCase();

        Map<String, String> errors = new LinkedHashMap<>();
        if (name.length() < 3 || name.length() > 100) errors.put("fullName", "Enter your full name.");
        if (!EMAIL.matcher(mail).matches()) errors.put("email", "Enter a valid email address.");
        if (password == null || password.length() < 8) errors.put("password", "Use at least 8 characters.");
        else if (!password.matches(".*\\d.*") || !password.matches(".*[A-Za-z].*")) {
            errors.put("password", "Mix letters and numbers.");
        }
        if (!group.isEmpty() && !GROUP.matcher(group).matches()) {
            errors.put("groupCode", "Group codes look like G07. Leave it empty if you have no group yet.");
        }
        if (errors.isEmpty() && users.emailExists(mail)) errors.put("email", "An account with this email already exists.");
        if (!errors.isEmpty()) throw new ValidationException(errors);

        String salt = PasswordHasher.newSalt();
        int id = users.insert(name, mail, PasswordHasher.hash(password, salt), salt, "STUDENT",
                group.isEmpty() ? null : group);
        return new User(id, name, mail, "STUDENT", group.isEmpty() ? null : group);
    }
}
