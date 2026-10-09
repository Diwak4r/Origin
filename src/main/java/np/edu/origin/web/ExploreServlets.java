package np.edu.origin.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import np.edu.origin.App;
import np.edu.origin.model.User;
import np.edu.origin.service.CsvWriter;
import np.edu.origin.service.NotFoundException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** Read-only pages: landing, gap map, ideas, Class Radar and the student's own history. */
public final class ExploreServlets {

    private ExploreServlets() { }

    /** The landing page. A signed-in user goes straight to their own first page. */
    @WebServlet("")
    public static class Home extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
            User user = Web.user(req);
            if (user != null) {
                Web.redirect(req, res, user.isSupervisor() ? "/admin" : "/app/check");
                return;
            }
            try {
                req.setAttribute("projectCount", App.get().corpus.count());
                Web.render(req, res, "home");
            } catch (SQLException e) {
                throw new ServletException(e);
            }
        }
    }

    @WebServlet("/app/ideas")
    public static class Ideas extends HttpServlet {
        private static final int PAGE_SIZE = 15;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
            try {
                App app = App.get();
                Integer level = Web.intParam(req, "level");
                Integer topic = Web.intParam(req, "topic");
                boolean taken = "1".equals(req.getParameter("taken"));
                List<np.edu.origin.model.Suggestion> all = app.ideas.list(null, null, level, topic, taken);
                int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
                int page = Math.min(Math.max(1, Web.intParam(req, "page", 1)), pages);
                req.setAttribute("ideas", all.subList((page - 1) * PAGE_SIZE, Math.min(all.size(), page * PAGE_SIZE)));
                req.setAttribute("total", all.size());
                req.setAttribute("page", page);
                req.setAttribute("pages", pages);
                req.setAttribute("topics", app.syllabus.topics());
                req.setAttribute("filter", Map.of(
                        "topic", topic == null ? 0 : topic,
                        "level", level == null ? 0 : level, "taken", taken));
                req.setAttribute("problemsSeed", app.ideaDao.countProblems("SEED"));
                req.setAttribute("problemsStudent", app.ideaDao.countProblems("STUDENT"));
                Web.render(req, res, "ideas");
            } catch (SQLException e) {
                throw new ServletException(e);
            }
        }
    }

    @WebServlet("/app/radar")
    public static class Radar extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
            try {
                App app = App.get();
                req.setAttribute("proposals", app.radar.radar());
                req.setAttribute("semester", app.lookups.activeSemesterLabel());
                req.setAttribute("myProposal", app.radar.lockedProposal(Web.user(req)));
                Web.render(req, res, "radar");
            } catch (SQLException e) {
                throw new ServletException(e);
            }
        }
    }

    @WebServlet({"/app/history", "/app/history.csv"})
    public static class History extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
            User user = Web.user(req);
            try {
                List<Map<String, Object>> rows = App.get().checkDao.history(user.getId());
                if (req.getRequestURI().endsWith(".csv")) {
                    res.setContentType("text/csv;charset=UTF-8");
                    res.setHeader("Content-Disposition", "attachment; filename=\"origin-checks.csv\"");
                    CsvWriter.write(res.getWriter(),
                            List.of("id", "checked_at", "title", "domain", "type", "verdict", "similarity_pct",
                                    "claims_flagged", "closest_past_project"), rows);
                    return;
                }
                req.setAttribute("rows", rows);
                Web.render(req, res, "history");
            } catch (SQLException e) {
                throw new ServletException(e);
            }
        }
    }
}
