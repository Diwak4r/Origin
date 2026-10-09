package np.edu.origin.web;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import np.edu.origin.App;
import np.edu.origin.model.CheckView;
import np.edu.origin.model.IdeaInput;
import np.edu.origin.model.MatchView;
import np.edu.origin.model.Suggestion;
import np.edu.origin.model.User;
import np.edu.origin.service.NotFoundException;
import np.edu.origin.service.ValidationException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The idea check: form, submit, result page, background status and locking for the group. */
public final class CheckServlets {

    private CheckServlets() { }

    @WebServlet("/app/check")
    public static class Form extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
            User user = Web.user(req);
            Map<String, Object> form = new HashMap<>();
            try {
                Integer ideaId = Web.intParam(req, "idea");
                Integer fromId = Web.intParam(req, "from");
                if (ideaId != null) {
                    Suggestion s = App.get().ideas.find(ideaId);
                    if (s != null) {
                        form.put("title", s.getTitle());
                        form.put("problem", s.getStatement());
                        form.put("tags", s.getTagText());
                        form.put("domainId", s.getDomainId());
                        form.put("typeId", s.getTypeId());
                        form.put("targetSemester", 4);
                        req.setAttribute("startedFrom", s);
                    }
                } else if (fromId == null && req.getParameter("title") != null && !req.getParameter("title").isBlank()) {
                    // typed on the home page: carry the sentence into the form
                    String typed = req.getParameter("title").strip();
                    form.put("title", typed.substring(0, Math.min(160, typed.length())));
                } else if (fromId != null) {
                    CheckView c = App.get().checks.view(fromId, user);
                    form.put("title", c.getTitle());
                    form.put("abstractText", c.getAbstractText());
                    form.put("problem", c.getProblem() == null ? "" : c.getProblem());
                    form.put("tags", String.join(", ", c.getTags()));
                    form.put("domainId", c.getDomainId());
                    form.put("typeId", c.getTypeId());
                    form.put("targetSemester", c.getTargetSemester());
                }
                show(req, res, form);
            } catch (NotFoundException e) {
                Web.notFound(req, res);
            } catch (SQLException e) {
                throw new ServletException(e);
            }
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
            IdeaInput in = new IdeaInput(req.getParameter("title"), req.getParameter("abstractText"),
                    req.getParameter("problem"), req.getParameter("tags"),
                    Web.intParam(req, "domainId", 0), Web.intParam(req, "typeId", 0),
                    Web.intParam(req, "targetSemester", 0));
            try {
                int id = App.get().checks.check(Web.user(req), in);
                Web.redirect(req, res, "/app/evaluating?id=" + id);
            } catch (ValidationException e) {
                Map<String, Object> form = new HashMap<>();
                form.put("title", in.getTitle());
                form.put("abstractText", in.getAbstractText());
                form.put("problem", in.getProblem());
                form.put("tags", in.getTags());
                form.put("domainId", in.getDomainId());
                form.put("typeId", in.getTypeId());
                form.put("targetSemester", in.getTargetSemester());
                req.setAttribute("errors", e.getFieldErrors());
                res.setStatus(422);
                try {
                    show(req, res, form);
                } catch (SQLException ex) {
                    throw new ServletException(ex);
                }
            } catch (SQLException e) {
                throw new ServletException(e);
            }
        }

        private void show(HttpServletRequest req, HttpServletResponse res, Map<String, Object> form)
                throws SQLException, ServletException, IOException {
            App app = App.get();
            req.setAttribute("form", form);
            req.setAttribute("domains", app.lookups.domains());
            req.setAttribute("types", app.lookups.types());
            List<String> tags = app.lookups.tagNames();
            req.setAttribute("tagNames", tags.subList(0, Math.min(36, tags.size())));
            req.setAttribute("corpusSample", app.corpus.sample(8));
            Web.render(req, res, "check");
        }
    }

    /** A short screen between the form and the result that shows what Origin actually looked at. */
    @WebServlet("/app/evaluating")
    public static class Evaluating extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
            Integer id = Web.intParam(req, "id");
            if (id == null) {
                Web.notFound(req, res);
                return;
            }
            try {
                App app = App.get();
                User user = Web.user(req);
                CheckView check = app.checks.view(id, user);
                req.setAttribute("check", check);
                req.setAttribute("ev", app.checks.evaluate(check, user));
                Web.render(req, res, "evaluating");
            } catch (NotFoundException e) {
                Web.notFound(req, res);
            } catch (SQLException e) {
                throw new ServletException(e);
            }
        }
    }

    @WebServlet("/app/result")
    public static class Result extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
            Integer id = Web.intParam(req, "id");
            if (id == null) {
                Web.notFound(req, res);
                return;
            }
            try {
                App app = App.get();
                User user = Web.user(req);
                CheckView check = app.checks.view(id, user);
                req.setAttribute("check", check);
                req.setAttribute("extras", app.checks.extras(check));
                req.setAttribute("domainIdeas", app.ideas.list(check.getDomainId(), null, null, false).stream().limit(3).toList());
                req.setAttribute("groupLocked", app.radar.lockedProposal(user));
                Web.render(req, res, "result");
            } catch (NotFoundException e) {
                Web.notFound(req, res);
            } catch (SQLException e) {
                throw new ServletException(e);
            }
        }
    }

    /** Polled by the result page while the background comparison runs. */
    @WebServlet("/app/result/status")
    public static class Status extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
            Integer id = Web.intParam(req, "id");
            res.setContentType("application/json;charset=UTF-8");
            res.setHeader("Cache-Control", "no-store");
            if (id == null) {
                res.setStatus(404);
                res.getWriter().write("{}");
                return;
            }
            try {
                CheckView c = App.get().checks.view(id, Web.user(req));
                JsonArrayBuilder matches = Json.createArrayBuilder();
                for (List<MatchView> pool : List.of(c.getHistory(), c.getClassMatches())) {
                    for (MatchView m : pool) {
                        matches.add(Json.createObjectBuilder().add("id", m.getId()).add("final", m.getPercent()));
                    }
                }
                res.getWriter().write(Json.createObjectBuilder()
                        .add("status", c.getEngineStatus())
                        .add("verdict", c.getVerdict())
                        .add("percent", c.getPercent())
                        .add("matches", matches)
                        .build().toString());
            } catch (NotFoundException e) {
                res.setStatus(404);
                res.getWriter().write("{}");
            } catch (SQLException e) {
                throw new ServletException(e);
            }
        }
    }

    @WebServlet("/app/lock")
    public static class Lock extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
            Integer id = Web.intParam(req, "checkId");
            if (id == null) {
                Web.notFound(req, res);
                return;
            }
            User user = Web.user(req);
            try {
                App.get().radar.lock(user, id);
                Web.flash(req, "Locked for group " + user.getGroupCode() + ". Your supervisor can now review it.");
                Web.redirect(req, res, "/app/radar");
            } catch (ValidationException e) {
                Web.flash(req, e.getMessage());
                Web.redirect(req, res, "/app/result?id=" + id);
            } catch (NotFoundException e) {
                Web.notFound(req, res);
            } catch (SQLException e) {
                throw new ServletException(e);
            }
        }
    }
}
