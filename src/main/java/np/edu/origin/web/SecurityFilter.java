package np.edu.origin.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import np.edu.origin.model.User;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * Runs before every request:
 *  1. adds security headers (CSP, no framing, no MIME sniffing),
 *  2. sends anonymous visitors to the login page, because only the landing page and the two
 *     account pages are open,
 *  3. keeps students out of /admin,
 *  4. rejects any POST without the session's CSRF token.
 */
@WebFilter("/*")
public class SecurityFilter extends HttpFilter {

    /** The only pages an anonymous visitor can read. Everything else needs an account. */
    private static final Set<String> OPEN_PAGES = Set.of("/", "/login", "/register", "/logout");

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        req.setCharacterEncoding("UTF-8");
        res.setHeader("Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; "
              + "form-action 'self'; frame-ancestors 'none'; base-uri 'self'");
        res.setHeader("X-Frame-Options", "DENY");
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("Referrer-Policy", "same-origin");

        String path = req.getRequestURI().substring(req.getContextPath().length());
        if (path.startsWith("/static/")) {
            chain.doFilter(req, res);
            return;
        }

        User user = Web.user(req);
        if (user == null && !OPEN_PAGES.contains(path)) {
            Web.redirect(req, res, "/login?next=" + java.net.URLEncoder.encode(path, StandardCharsets.UTF_8));
            return;
        }
        if (path.startsWith("/admin") && !user.isSupervisor()) {
            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            req.setAttribute("errorTitle", "Supervisors only");
            req.setAttribute("errorText", "This part of Origin is for project supervisors.");
            Web.render(req, res, "error");
            return;
        }
        if ("POST".equals(req.getMethod()) && !validCsrf(req)) {
            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            req.setAttribute("errorTitle", "Form expired");
            req.setAttribute("errorText", "The page was open too long or came from somewhere else. Go back, reload and try again.");
            Web.render(req, res, "error");
            return;
        }

        req.setAttribute("currentUser", user);
        req.setAttribute("csrf", Web.csrfToken(req));
        req.setAttribute("path", path);
        HttpSession s = req.getSession(false);
        if (s != null && s.getAttribute(Web.FLASH) != null) {
            req.setAttribute("flash", s.getAttribute(Web.FLASH));
            s.removeAttribute(Web.FLASH);
        }
        chain.doFilter(req, res);
    }

    private static boolean validCsrf(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        if (s == null) return false;
        String expected = (String) s.getAttribute(Web.CSRF);
        String sent = req.getParameter("_csrf");
        if (expected == null || sent == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), sent.getBytes(StandardCharsets.UTF_8));
    }
}
