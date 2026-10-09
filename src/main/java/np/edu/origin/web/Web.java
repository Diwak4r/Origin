package np.edu.origin.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import np.edu.origin.model.User;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;

/** Small helpers shared by every servlet. */
public final class Web {

    static final String USER = "user";
    static final String CSRF = "csrf";
    static final String FLASH = "flash";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Web() { }

    public static User user(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return s == null ? null : (User) s.getAttribute(USER);
    }

    /** Stores the user in a fresh session. A new session id on login blocks session fixation. */
    static void signIn(HttpServletRequest req, User user) {
        HttpSession old = req.getSession(false);
        if (old != null) old.invalidate();
        HttpSession s = req.getSession(true);
        s.setAttribute(USER, user);
        s.setAttribute(CSRF, newToken());
        s.setMaxInactiveInterval(60 * 60 * 2);
    }

    static String csrfToken(HttpServletRequest req) {
        HttpSession s = req.getSession(true);
        String t = (String) s.getAttribute(CSRF);
        if (t == null) {
            t = newToken();
            s.setAttribute(CSRF, t);
        }
        return t;
    }

    private static String newToken() {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    /** One-time message shown on the next page, after a redirect. */
    static void flash(HttpServletRequest req, String message) {
        req.getSession(true).setAttribute(FLASH, message);
    }

    static void render(HttpServletRequest req, HttpServletResponse res, String view)
            throws ServletException, IOException {
        req.getRequestDispatcher("/WEB-INF/jsp/" + view + ".jsp").forward(req, res);
    }

    static void redirect(HttpServletRequest req, HttpServletResponse res, String path) throws IOException {
        res.sendRedirect(req.getContextPath() + path);
    }

    /** Parses an int parameter; returns null for missing or malformed values instead of throwing. */
    static Integer intParam(HttpServletRequest req, String name) {
        String v = req.getParameter(name);
        if (v == null || v.isBlank()) return null;
        try {
            return Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static int intParam(HttpServletRequest req, String name, int fallback) {
        Integer v = intParam(req, name);
        return v == null ? fallback : v;
    }

    static void notFound(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        res.setStatus(HttpServletResponse.SC_NOT_FOUND);
        req.setAttribute("errorTitle", "Nothing here");
        req.setAttribute("errorText", "That page does not exist, or it belongs to someone else.");
        render(req, res, "error");
    }
}
