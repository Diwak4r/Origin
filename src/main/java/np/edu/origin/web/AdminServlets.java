package np.edu.origin.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import np.edu.origin.App;
import np.edu.origin.service.CsvWriter;
import np.edu.origin.service.NotFoundException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Supervisor desk: review locked ideas, correct matches, close the semester, read the statistics. */
public final class AdminServlets {

    private static final Pattern SEMESTER_CODE = Pattern.compile("^\\d{4}-(SPRING|FALL)$");

    private AdminServlets() { }

    @WebServlet("/admin")
    public static class Desk extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
            try {
                App app = App.get();
                req.setAttribute("pending", app.proposals.radar("PENDING"));
                req.setAttribute("reviewed", app.proposals.radar(null).stream()
                        .filter(p -> !"PENDING".equals(p.getStatus())).toList());
                req.setAttribute("semester", app.lookups.activeSemesterLabel());
                req.setAttribute("saturation", app.admin.saturation());
                req.setAttribute("trends", app.admin.tagTrends(12));
                req.setAttribute("audit", app.admin.recentAudit(14));
                req.setAttribute("ingest", app.ingestLog.recent(12));
                req.setAttribute("dbFolder", app.watcher.folder().toAbsolutePath().normalize().toString());
                req.setAttribute("stats", Map.of(
                        "checks", app.checkDao.countAll(),
                        "projects", app.corpus.count(),
                        "proposals", app.proposals.count(),
                        "studentProblems", app.ideaDao.countProblems("STUDENT"),
                        "ingested", app.ingestLog.countDone()));
                Web.render(req, res, "admin");
            } catch (SQLException e) {
                throw new ServletException(e);
            }
        }
    }

    @WebServlet("/admin/review")
    public static class Review extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
            Integer id = Web.intParam(req, "proposalId");
            String decision = req.getParameter("decision");
            if (id == null || !("APPROVED".equals(decision) || "REJECTED".equals(decision))) {
                Web.notFound(req, res);
                return;
            }
            try {
                App.get().proposals.review(id, decision, Web.user(req).getId(), req.getParameter("note"));
                Web.flash(req, "APPROVED".equals(decision) ? "Approved. It joins the corpus when the semester closes."
                                                           : "Sent back to the group.");
                Web.redirect(req, res, "/admin");
            } catch (SQLException e) {
                throw new ServletException(e);
            }
        }
    }

    @WebServlet("/admin/override")
    public static class OverrideMatch extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
            Integer checkId = Web.intParam(req, "checkId");
            Integer matchId = Web.intParam(req, "matchId");
            if (checkId == null || matchId == null) {
                Web.notFound(req, res);
                return;
            }
            boolean same = "same".equals(req.getParameter("verdict"));
            try {
                App.get().checks.override(Web.user(req), checkId, matchId, same);
                Web.flash(req, "Saved. Origin will remember this for the same idea next time.");
                Web.redirect(req, res, "/app/result?id=" + checkId);
            } catch (NotFoundException e) {
                Web.notFound(req, res);
            } catch (SQLException e) {
                throw new ServletException(e);
            }
        }
    }

    @WebServlet("/admin/archive")
    public static class Archive extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
            String code = AuthServlets.nz(req.getParameter("code")).trim().toUpperCase();
            String label = AuthServlets.nz(req.getParameter("label")).trim();
            if (!"yes".equals(req.getParameter("confirm"))) {
                Web.flash(req, "Tick the confirmation box to close the semester.");
            } else if (!SEMESTER_CODE.matcher(code).matches() || label.length() < 4) {
                Web.flash(req, "Use a code like 2027-SPRING and a readable label.");
            } else {
                try {
                    int moved = App.get().admin.archiveSemester(code, label, Web.user(req).getId());
                    App.get().refreshIdeasLater();
                    Web.flash(req, "Semester closed. " + moved + " approved projects joined the corpus and " + label + " is open.");
                } catch (SQLException e) {
                    Web.flash(req, "Could not close the semester: " + e.getMessage());
                }
            }
            Web.redirect(req, res, "/admin");
        }
    }

    @WebServlet("/admin/report.csv")
    public static class Report extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
            try {
                List<Map<String, Object>> rows = App.get().admin.semesterReport();
                res.setContentType("text/csv;charset=UTF-8");
                res.setHeader("Content-Disposition", "attachment; filename=\"origin-semester-report.csv\"");
                CsvWriter.write(res.getWriter(), List.of("group", "title", "domain", "type", "status",
                        "similarity_pct", "band", "collisions"), rows);
            } catch (SQLException e) {
                throw new ServletException(e);
            }
        }
    }
}
