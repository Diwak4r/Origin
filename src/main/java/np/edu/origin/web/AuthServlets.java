package np.edu.origin.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import np.edu.origin.App;
import np.edu.origin.model.User;
import np.edu.origin.service.ValidationException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Login, registration and logout. */
public final class AuthServlets {

    private AuthServlets() { }

    /** Where to go after login. Only local paths are allowed, so the link cannot send users to another site. */
    static String safeNext(String next) {
        if (next == null || !next.startsWith("/") || next.startsWith("//") || next.contains("\\")) return "/app/check";
        return next;
    }

    @WebServlet("/login")
    public static class Login extends HttpServlet {

        /** Failed attempts per client address: at most 5 in any 5-minute window. */
        private static final int MAX_FAILURES = 5;
        private static final long WINDOW_MS = 5 * 60 * 1000;
        private final Map<String, long[]> failures = new ConcurrentHashMap<>();

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
            if (Web.user(req) != null) {
                Web.redirect(req, res, "/app/check");
                return;
            }
            req.setAttribute("next", optionalNext(req.getParameter("next")));
            Web.render(req, res, "login");
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
            String ip = req.getRemoteAddr();
            String email = req.getParameter("email");
            String next = optionalNext(req.getParameter("next"));
            req.setAttribute("email", email);
            req.setAttribute("next", next);
            if (tooMany(ip)) {
                res.setStatus(429);
                req.setAttribute("error", "Too many wrong attempts. Wait five minutes and try again.");
                Web.render(req, res, "login");
                return;
            }
            try {
                User user = App.get().auth.login(email, req.getParameter("password"));
                if (user == null) {
                    recordFailure(ip);
                    req.setAttribute("error", "That email and password do not match an account.");
                    Web.render(req, res, "login");
                    return;
                }
                failures.remove(ip);
                Web.signIn(req, user);
                String home = user.isSupervisor() ? "/admin" : "/app/check";
                Web.redirect(req, res, next == null ? home : next);
            } catch (SQLException e) {
                throw new ServletException(e);
            }
        }

        private boolean tooMany(String ip) {
            long[] f = failures.get(ip);
            return f != null && f[0] >= MAX_FAILURES && System.currentTimeMillis() - f[1] < WINDOW_MS;
        }

        private void recordFailure(String ip) {
            long now = System.currentTimeMillis();
            failures.compute(ip, (k, f) -> (f == null || now - f[1] > WINDOW_MS) ? new long[]{1, now} : new long[]{f[0] + 1, f[1]});
        }
    }

    @WebServlet("/register")
    public static class Register extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
            Web.render(req, res, "register");
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
            try {
                User user = App.get().auth.register(req.getParameter("fullName"), req.getParameter("email"),
                        req.getParameter("password"), req.getParameter("groupCode"));
                Web.signIn(req, user);
                Web.flash(req, "Welcome, " + user.getFirstName() + ". Start by checking your first idea.");
                Web.redirect(req, res, "/app/check");
            } catch (ValidationException e) {
                req.setAttribute("errors", e.getFieldErrors());
                req.setAttribute("form", Map.of(
                        "fullName", nz(req.getParameter("fullName")),
                        "email", nz(req.getParameter("email")),
                        "groupCode", nz(req.getParameter("groupCode"))));
                Web.render(req, res, "register");
            } catch (SQLException e) {
                throw new ServletException(e);
            }
        }
    }

    @WebServlet("/logout")
    public static class Logout extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
            HttpSession s = req.getSession(false);
            if (s != null) s.invalidate();
            Web.redirect(req, res, "/");
        }
    }

    /** A safe local "next" path, or null when none was given. */
    static String optionalNext(String next) {
        return next == null || next.isBlank() ? null : safeNext(next);
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }
}
