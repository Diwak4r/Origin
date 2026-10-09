# Origin (Final) - Spec

Team: Sandesh Dotel, Bhumika Karki, Diwakar Ray Yadav. BIT 4th sem Project-IV (BIT256CO).

## One line
A web app that tells a student, at proposal time, whether their project idea already exists (past corpus or a
classmate's live proposal), shows **where the untouched gaps are**, and **keeps its own database current** every
semester without anyone maintaining it.

## Users
| Role | Can do |
|---|---|
| Student | Register/login, check an idea, see similar projects + gaps, lock ONE idea for the group, see Class Radar |
| Supervisor | Everything above + approve/reject locked ideas, override a verdict, archive a semester, view analytics |

## Features (IN scope)
1. **Similarity check** - title + abstract + tags + type compared against (a) the historical corpus and (b) this
   semester's locked class proposals. Explainable rule score (tag Jaccard, keyword Jaccard, type match) shown
   as a breakdown. Bands: ORIGINAL / SIMILAR / DUPLICATE / LOW-CONFIDENCE.
2. **Jev second opinion (background)** - for the top 3 rule matches, a background thread asks Jev
   `noul: "Is idea A essentially the same project as B?"`, `noul: "Are the advanced claims (AI, prediction...) substantiated
   by the description?"`, `choice: domain`, `score: novelty 0-4`. Probabilities are stored and shown next to the rule score.
   No API key / API down -> app works on rules alone and says "AI opinion unavailable".
3. **Project Gap finder** - domain x technology matrix built from SQL aggregates over the corpus; empty/thin cells are
   "gaps". Student sees under-explored areas + saturated areas before choosing.
4. **Class Radar** - each group locks one idea per semester. When a new idea is locked, every existing locked idea is
   re-scored against it automatically; both groups see a COLLISION flag.
5. **Self-updating database (Sir's request)**
   - Trigger: a check inserts -> tag/domain popularity counters update themselves.
   - Trigger: supervisor approves a proposal -> audit row written.
   - Stored procedure `archive_semester` (transaction): approved proposals become corpus projects, class radar resets,
     gap matrix refreshes. The corpus grows every semester on its own.
   - Jev auto-classifies any new corpus project into a domain (no manual tagging of domain).
   - Background maintenance thread (every N minutes): refresh gap snapshot, re-check pending ideas, retry failed Jev calls.
   - Supervisor overrides are stored and applied to future checks of the same pair (the system remembers corrections).
6. **Views** used by the UI: `v_domain_saturation`, `v_class_radar`, `v_idea_history`.
7. **Reports** - CSV export of a student's check history and of the supervisor's semester report (Java file IO).
8. **Security** - salted SHA-256 password hashes, session login, role-based page access, PreparedStatement everywhere,
   CSRF token on forms.

## OUT of scope
Mobile app, email notifications, external plagiarism APIs, prose/code plagiarism, multi-college tenancy.

## Tech (syllabus only: BIT255CO + BIT254CO)
Java 17 · Servlet + JSP (+JSTL) on Apache Tomcat 10 (embedded launcher so one command runs it) · JDBC with
PreparedStatement · MySQL 8 (tables, FKs, views, triggers, procedures, transactions) · java.net.http for Jev ·
Threads for background work · File IO for seed CSV + exports · Maven build.

## Edge cases
- Corpus < 15 rows -> LOW-CONFIDENCE band.
- Idea with no tags -> validation error, not a crash.
- Group tries to lock a second idea -> refused (UNIQUE(group, semester)).
- Jev 401/429/timeout -> stored as FAILED, retried by the background thread, never blocks the page.
- Renamed idea ("Library System" -> "Book Depot Portal") -> synonym table + tags still catch it.
- Buzzword padding ("AI", "prediction") without substance -> flagged by rule detector and Jev noul.
