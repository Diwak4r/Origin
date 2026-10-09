"""Brings the final report up to Origin v3 (DB folder, Syllabus Map, new matching, no live decision engine).

build_report.py holds the v2 text. This module wraps the Writer methods and changes only what v3 changed:
paragraphs, bullets, tables, figures and code listings are matched by their opening words, replaced or
dropped, and new sections are inserted. Every rule must fire exactly where intended: verify() fails the
build if a rule never matched, so no stale v2 sentence can slip through unnoticed.
"""
from collections import Counter

USED = Counter()
RULE_KEYS = set()

# ---------------------------------------------------------------- front matter

ABSTRACT = (
    "Every semester, student groups in project-based programmes propose ideas that repeat earlier projects or clash "
    "with a classmate's idea, and the overlap is usually found late. Origin is a web application that checks a project "
    "idea at proposal time and suggests what to build instead. Past projects are loaded by copying CSV, TXT, XLSX or "
    "DOCX files into a folder that Origin watches. A student types an idea and a map of 41 topics from the BIT syllabus "
    "reacts at once: worked ground turns red and untouched topics stay open. A full check scores the idea against the "
    "past projects and against the ideas other groups have locked, using three explainable signals (shared tags, shared "
    "words and project type) in which rare terms count more, and it tells each match as a story of what is shared, what "
    "is new and what to change. A synonym table catches renamed ideas and a claim detector flags buzzwords that the "
    "description does not support. The Ideas page turns 42 real problems from Nepal into suggestions, each tied to "
    "syllabus topics and shown with its proof, such as how many past projects used the same pair of topics. The MySQL "
    "database keeps itself current through triggers, views and stored procedures. The system is built with Java Servlets "
    "and Jakarta Server Pages on Apache Tomcat with a MySQL database. All 84 automated tests pass, and the matching "
    "formula agrees with human labels on 22 of 25 labelled pairs, as often as the formula it replaced."
)
KEYWORDS = "project originality, syllabus map, similarity scoring, Java Servlets, MySQL triggers, term weighting"
EXTRA_ABBREVIATIONS = [
    ("DOCX", "Office Open XML document (Word)"), ("XLSX", "Office Open XML spreadsheet (Excel)"),
    ("XML", "Extensible Markup Language"), ("TXT", "Plain text file"),
]

# ---------------------------------------------------------------- paragraph rules (opening words -> new text, or None to drop)

P = {}
AFTER_P = {}      # opening words -> function(writer, originals) that adds content after the paragraph


def rule_p(prefix, new):
    P[prefix] = new


# Chapter 1
rule_p("Origin is built for that moment. It compares the structure",
       "Origin is built for that moment. It compares the structure of an idea with past projects and with this "
       "semester's locked ideas, explains every number it shows, and then goes one step further: it shows which topics "
       "of the syllabus no past project has built on, and suggests real problems that no group has solved yet, each one "
       "made from those topics. Past projects reach the system the easy way: a supervisor copies the college's files "
       "into a folder, and Origin reads them by itself.")
rule_p("To design and develop Origin, a web application that checks",
       "To design and develop Origin, a web application that loads past projects from files dropped into a folder, "
       "checks a student project idea against them and against this semester's locked ideas at proposal time, shows "
       "which syllabus topics are untouched, and suggests real problems worth building, with a database that keeps its "
       "own records current.")

# Chapter 2
rule_p("**Decision engines.**",
       "**Syllabus topics.** Origin describes each topic of the fourth-semester syllabus, such as page replacement, "
       "triggers or the t-test, with a short list of keywords. A past project is linked to every topic whose keywords "
       "appear in its title, description or tech stack. Counting the projects per topic, and per pair of topics, gives "
       "the Syllabus Map and the proof shown beside each suggestion. The method is plain keyword matching on purpose: "
       "anyone can check a count by hand, and a wrong count points straight at the keyword that caused it.")
rule_p("Technically, every part of Origin uses topics from the fourth-semester syllabus",
       "Technically, every part of Origin uses topics from the fourth-semester syllabus: Java, JDBC, Servlets and JSP "
       "from Programming in Java, and SQL, triggers, procedures, normalisation and transactions from Database "
       "Management System. Operationally, students already write a title and a short description for every proposal, "
       "so Origin asks for nothing new. Economically, all tools are free. Table 2.3 summarises the analysis.")


def after_set_similarity(w, O):
    O.p(w, "**Term weighting.** A word that only a few past projects used says more about an idea than a word that most "
           "of them used. Origin weighs each term by 1 / (1 + ln(1 + n)), where n is the number of past projects that "
           "use it, the same idea as inverse document frequency in information retrieval [4]. A term nobody used weighs "
           "1.0 and a term used by nine projects weighs about 0.3. Overlap is then measured in two ways and combined 80% "
           "and 20%: the weighted Jaccard index, and the share of the idea's own weight that the other project already "
           "has. The second part stops a small idea that sits inside a large old project from being scored as half "
           "similar.")


AFTER_P["**Set similarity.**"] = after_set_similarity

# Chapter 3
rule_p("We followed the iterative and incremental model",
       "We followed the iterative and incremental model [16]. The system was split into five increments, and each one "
       "went through planning, design, building and testing before the next began (Figure 3.1). The first increment "
       "delivered the schema, the seed data and login. The second added the rule engine and the check flow, which made "
       "Origin usable on its own. The third added Class Radar and the gap map, the fourth the idea generator and the "
       "DB-folder import, and the fifth the Syllabus Map, the suggester, the full test suite and this report.")
rule_p("Origin uses a layered client-server architecture",
       "Origin uses a layered client-server architecture (Figure 3.2). The browser receives HTML rendered from JSP pages. "
       "Every request first passes through a security filter, which checks the session, the role and the CSRF token and "
       "adds the security headers. Servlets act as controllers: they read the request, call a service and forward the "
       "result to a JSP view. One small servlet, /peek, answers the live preview with JSON while a visitor types.")
rule_p("The service layer holds the logic.",
       "The service layer holds the logic. CheckService validates and scores ideas, RadarService handles locking and "
       "clashes, IdeaService builds the suggestions, IngestService reads the files from the DB folder, SyllabusService "
       "links projects to topics, GapService prepares the map and AuthService signs people in. The similarity engine "
       "sits beside them as plain Java classes with no database access, which is what makes it easy to test. The data "
       "access layer uses JDBC with PreparedStatement for every query, transactions for every multi-step write, and "
       "CallableStatement for the stored procedures.")
rule_p("Two thread pools run beside the request threads.",
       "Three background jobs run beside the request threads. A watcher thread looks in the DB folder every five "
       "seconds and hands each new or changed file to IngestService. A timer thread refreshes the idea suggestions "
       "every ten minutes, and a small worker pool refreshes them after every lock and every import. The folder is "
       "outside the system: when it is empty, the watcher simply does nothing.")
rule_p("At the highest level, Origin exchanges data with two people and one external system",
       "At the highest level, Origin exchanges data with two people and one external source (Figure 3.3). Students send "
       "ideas, tags and lock requests and receive verdicts, matches, the syllabus map, suggestions and the radar. "
       "Supervisors send review decisions, corrections and the command to close a semester, and receive the locked "
       "ideas, clashes, the ingest log and the semester report. The DB folder supplies past-project files.")
rule_p("Figure 3.4 breaks the system into six processes",
       "Figure 3.4 breaks the system into six processes and seven data stores. Authentication (1.0) reads the users. "
       "Checking an idea (2.0) reads the corpus and the locked proposals and writes the check with its matches. Locking "
       "(3.0) writes the proposal and its clashes, and the audit log is written by triggers. Building the map and ideas "
       "(4.0) reads the corpus, its topics and the proposals and writes the suggestions. Review and archiving (5.0) "
       "updates proposals and, at the end of the semester, writes approved ideas into the corpus. Reading the DB folder "
       "(6.0) takes files from the folder, adds new projects to the corpus with their topics, and writes one line per "
       "file to the ingest log.")
rule_p("The database has 19 tables in third normal form",
       "The database has 25 tables in third normal form (Figure 3.5). The historical corpus lives in corpus_projects, "
       "linked to tags through corpus_project_tags and to syllabus topics through corpus_project_topics. Every check a "
       "student runs is stored in idea_checks with its tags in idea_check_tags and its scored matches in check_matches. "
       "A match points either to a corpus project or to a proposal, never both, which a check constraint enforces. When "
       "a group locks a check it becomes a row in proposals, whose composite unique key on group_code and semester_id "
       "enforces one idea per group per semester. Clashes between proposals are rows in radar_collisions.")
rule_p("The ideas side has its own tables",
       "The ideas side has its own tables: problem_bank holds real problems with their tags in problem_tags and their "
       "syllabus topics in problem_topics, problem_twist holds one sentence per problem, and idea_suggestions holds one "
       "row per problem and suitable project type, with suggestion_proof storing the numbers shown as its proof. "
       "Supervisor corrections live in match_overrides and the audit_log is written only by triggers. engine_cache "
       "belongs to an optional integration that is switched off in this version. Domains, project types and semesters "
       "are small reference tables.")


def after_ideas_side(w, O):
    O.p(w, "Version 3 added six tables: ingest_log (one row per file version read from the DB folder), "
           "syllabus_topics (the 41 topics, each with its course, unit and keywords), corpus_project_topics, "
           "problem_topics, problem_twist and suggestion_proof. The table corpus_projects also gained a tech column, "
           "because the tech stack is where syllabus topics show. An upgrade step adds the column to a database made by "
           "an earlier version, so no data is lost.")


AFTER_P["The ideas side has its own tables"] = after_ideas_side
rule_p("As shown in Figure 3.6, the system supports thirteen primary use cases",
       "As shown in Figure 3.6, the system supports thirteen primary use cases across two actor roles, plus two use "
       "cases started by the system itself. A student registers, tries an idea on the Syllabus Map, checks an idea, "
       "reads the result, browses ideas, locks an idea for the group, views the Class Radar and downloads the check "
       "history. A supervisor can do everything a student can and also reviews locked ideas, corrects matches, closes "
       "the semester, downloads the semester report and drops past-project files into the DB folder. The folder "
       "watcher and the timer drive the reading of files and the refresh of suggestions.")
rule_p("Figure 3.7 follows one idea from sign-in to lock.",
       "Figure 3.7 follows one idea from sign-in to lock. Invalid input loops back with field errors. A valid idea is "
       "normalised, scored against both pools, adjusted by any remembered supervisor correction, banded and saved in one "
       "transaction. An evaluation screen then shows what Origin looked at for about two and a half seconds, and the "
       "result page opens. Locking is refused for a duplicate or for a group that has already locked an idea; otherwise "
       "the proposal is saved, compared with every other group's idea, and any clash is recorded.")

# Chapter 4
rule_p("Origin has seven modules.",
       "Origin has eight modules. Each description below points to the classes that implement it and to the "
       "screenshots taken from the running system.")
rule_p("The engine is plain Java with no database access.",
       "The engine is plain Java with no database access. An idea becomes a Candidate: a set of canonical tags, a set "
       "of meaningful keywords and a project type. Three scorers implement one Scorer interface, and SimilarityEngine "
       "loops over a List<Scorer> without knowing which concrete class it is calling. This is where inheritance and "
       "polymorphism do real work in the project. The scorers compare two sets with Overlap, which counts each term by "
       "the weight kept in TermStats.")
rule_p("The weights and bands are fixed and published on the check page",
       "The weights and bands are fixed and published, as Table 4.2 shows. Tags weigh most because they describe what "
       "a project does, and function does not change when the title does. Each term counts by its weight, "
       "1 / (1 + ln(1 + n)), where n is the number of past projects that use it, so a word used by one project says "
       "more than a word used by sixty. Overlap is 80% the weighted Jaccard index and 20% the share of the idea's own "
       "weight that the other project already has. The type counts only when the tags or words already overlap, so two "
       "unrelated web applications do not start at 20%. The same thresholds are written once in Java and once in the "
       "SQL function fn_band, and a test checks that they agree.")


def after_guards(w, O):
    O.p(w, "Three more parts make the result easy to read and quick to compute. Coverage measures how much of the "
           "idea's weight the three closest projects hold together, which shows a mash-up of two old projects. Story "
           "splits each match into the terms shared, the terms only the idea has and the terms only the old project "
           "has, with tags first, and writes one sentence on what to change. MatchIndex keeps, for each term, the list "
           "of projects that contain it, like the index at the back of a book, so a check scores only the projects "
           "that share at least one term. A project that shares no term scores zero anyway, so the answer is the same "
           "as scoring all of them, and a test compares the two.")


AFTER_P["Two guards sit beside the scorers."] = after_guards


def evaluation_and_rename(w, O, IMG):
    O.p(w, "CheckService validates the input, builds the Candidate, ranks the five closest past projects and the "
           "three closest locked class ideas, applies any remembered supervisor correction, and saves the check, its "
           "tags and its matches in one transaction. The student then sees an evaluation screen for about two and a "
           "half seconds (Figure 4.3). It lists the stages Origin went through with their real counts, for example 32 "
           "terms found, 71 projects searched of which 38 share a term, and 8 locked ideas compared. A link skips "
           "ahead, and a visitor who prefers reduced motion goes straight to the result.")
    O.figure(w, "4.3", IMG / "f56-evaluating.png", "Screenshot - Evaluation Screen with Real Counts", 15.0, 11)
    O.p(w, "Figure 4.4 shows the rename test from Chapter 1. \"Book Depot Portal\", described as students reserving "
           "books and getting reminders, scores 62% and lands in the Already done band. Its closest past project is "
           "the Online Library Book Lending Portal from 2021: 58% of tags and 44% of words in common, and the same "
           "type. The page tells the match as a story: booking, library and student are shared; notification and "
           "depot belong only to this idea; search and receive belong only to the old project. One sentence sums it "
           "up: the real difference is notification and depot, and that should be the core of the project. Group "
           "G01's Library Study Seat Booking shares half the tags but only 11% of the words, so it stays at 46%: "
           "reserving a seat is not reserving a book. The three closest projects together cover 64% of the idea.")


P["CheckService validates the input, builds the Candidate"] = None          # replaced by evaluation_and_rename
AFTER_P["The check form asks for a title"] = None                          # figure 4.2 follows; the new text goes after it
rule_p("Figure 4.4 shows the opposite case.",
       "Figure 4.5 shows the opposite case. A health-post medicine stock alert scores 30%, falls in the Looks original "
       "band, and the page offers to lock it for group G09. Its closest past project, the Student Attendance Management "
       "System, shares only a dashboard with it, and what only this idea has is medicine and inventory. The three "
       "closest projects together cover 17% of the idea.")
rule_p("RadarService locks an idea for the student's group.",
       "RadarService locks an idea for the student's group. It refuses when the account has no group, when the verdict "
       "is Already done, or when the group has already locked an idea. The last rule is enforced twice: once in Java "
       "for a friendly message, and once by the unique key on the proposals table, which stops two teammates who click "
       "at the same moment. A lock always uses the final verdict. After the insert, the new idea is scored against "
       "every other locked idea, and every pair at 35% or above is written to radar_collisions in the same "
       "transaction. A trigger copies each clash into the audit trail. In the seed data, group G06's Verified Blood "
       "Request Line and group G07's Rapid Blood Request Relay clash at 41%, and both rows of Figure 4.6 show it.")
rule_p("The gap map is the v_gap_matrix view",
       "The gap map is the v_gap_matrix view: a cross join of 12 domains and 6 project types with a count of past "
       "projects, locked ideas and open suggestions for each of the 72 cells. With the seed data, 40 cells have no past "
       "project at all. Selecting a cell lists what was built there, what is locked there this semester, and which open "
       "ideas fit it (Figure 4.7).")
P["IdeaService pairs each of the 42 problems in the problem bank"] = None   # moved into 4.2.6
P["When a key is configured, CheckService hands the new check"] = None
P["For suggestions, IdeaService asks whether the team can build"] = None
rule_p("The supervisor desk (Figure 4.8) lists the locked ideas waiting for review",
       "The supervisor desk (Figure 4.11) lists the locked ideas waiting for review with their clashes, the DB folder "
       "with the log of the files it has read, the share of past projects per domain from the v_domain_saturation "
       "view, the tags students check most, and the audit trail. On any result page, a supervisor can mark a match as "
       "the same project or a different one. The correction is stored against the idea's fingerprint, a SHA-256 hash "
       "of its sorted tags and keywords, so the same idea checked again later receives the corrected verdict without "
       "asking anyone.")
rule_p("We tested at three levels.",
       "We tested at three levels. Unit tests with JUnit 5 cover the scorers, the term weights, the overlap, coverage "
       "and story, the inverted index, the three file readers, the topic tagger, the suggester's scoring, the "
       "vocabulary, the claim detector, the password hasher, the CSV writer and the SQL script splitter. Integration "
       "tests run the real services against separate MySQL databases named origin_test and origin_ingest_test, which "
       "are dropped and rebuilt on every run, so they exercise the triggers, views, constraints, stored procedures and "
       "the folder watcher with real files. System tests drove the running application in Chrome from a Node script: "
       "typing into the home page, clicking, submitting checks, dropping real files into the DB folder and recording "
       "the console. HTTP checks with curl covered the security rules.")
rule_p("The accuracy of the engine was tested against human judgement.",
       "The accuracy of the formula was tested against human judgement. Before running it, the team labelled 25 pairs "
       "of ideas as Looks original, Overlaps or Already done: the 15 used for the first version and 10 that stress "
       "small ideas, renames and name-only collisions. The first version of the new formula agreed on 19 of the 25 "
       "pairs while the old formula agreed on 22, so it was not accepted. A sweep over the share given to the second "
       "overlap measure showed that giving it half of the overlap cost three pairs, so the share was cut to 20% and "
       "the original weights were kept. The accepted formula agrees on 22 of 25, the same as the old one, and on 13 of "
       "the original 15. The test fails if the new formula agrees on fewer pairs than the old one, or on fewer than "
       "12 of the original 15.")

# Chapter 5
rule_p("The final system runs on Apache Tomcat with MySQL",
       "The final system runs on Apache Tomcat with MySQL and serves twelve pages to two roles. The landing page opens "
       "on one prompt, \"I want to build\", over the Syllabus Map (Figure 5.1). Typing a library idea turns the readout "
       "to Already done and five plots red; typing an idea built on the Banker's algorithm leaves the map cool and "
       "reads Looks original. With the demo data, 16 of the 41 topics have never been used by a past project.")
rule_p("Further down, the landing page runs the rename test",
       "Further down, the landing page runs the rename test through the live engine every time it loads (Figure 5.2). "
       "\"Book Depot Portal\" is matched to the Online Library Book Lending Portal with 77% of tags and 61% of words in "
       "common and the same type, for 76% overall. The number is computed, not typed, so it stays true if the corpus "
       "changes.")
rule_p("**Outcome:** The system uses three layers",
       "**Outcome:** The system uses three layers (Figure 3.2) and 25 tables in third normal form (Figure 3.5). Six "
       "triggers, four views, one stored function and two stored procedures do work on their own (Table 4.3). TC-14 "
       "and TC-15 prove the triggers and the archive procedure against a real database.")
rule_p("**Outcome:** Every score on the result page is split",
       "**Outcome:** Every score on the result page is split into tags, words and type and told as a story (Figure "
       "4.4). The renamed library idea is caught at 62% (TC-07) and padding is flagged (TC-11). The new formula agrees "
       "with the team's human labels on 22 of 25 pairs, as often as the old one (TC-20).")
rule_p("**Outcome:** The gap map shows all 72 cells",
       "**Outcome:** The map shows all 41 syllabus topics with their counts and filters the ideas (Figures 4.8 and "
       "4.9), and the gap map keeps all 72 domain and type cells (Figure 4.7). The suggester turns 42 problems into 84 "
       "scored suggestions with proof lines: 77 are open, 7 are marked as taken by locked groups, and none is plain "
       "CRUD (Figure 4.10).")
rule_p("**Outcome:** Locking, clash detection and review work end to end",
       "**Outcome:** Locking, clash detection and review work end to end (Figures 4.6 and 4.11; TC-09, TC-10). The G06 "
       "and G07 clash is detected at 41%. A real Excel file and a CSV dropped into the DB folder were read within "
       "8.0 seconds, with the duplicate skipped and the bad row reported (TC-24, TC-25).")
rule_p("**Outcome:** All 35 automated tests pass",
       "**Outcome:** All 84 automated tests pass, the security rules hold over real HTTP (TC-16 to TC-18), and the "
       "timings in Section 5.4 are well inside the two-second requirement.")
rule_p("We timed the running system on a development laptop",
       "We timed the running system on a development laptop with MySQL on the same machine, averaging seven requests "
       "per page after one warm-up request, while other programs were using the laptop. Table 5.1 shows the results.")
rule_p("Every request finished in under 110 milliseconds",
       "Every request finished in under 0.4 seconds, far inside the two-second requirement. Each request opens its own "
       "database connection, which accounts for most of the time. The live preview that runs while a visitor types "
       "takes about 65 milliseconds. A check scores only the projects that share a term with the idea, because "
       "MatchIndex looks them up instead of scanning the corpus.")
rule_p("For usability, we checked the pages at desktop and phone widths",
       "For usability, we checked the pages at desktop and phone widths and in a dark colour scheme with scripted "
       "Chrome runs, and fixed every problem we found before release. No page scrolls sideways at 390 pixels. The map "
       "plots are real buttons, so the keyboard works, and the evaluation screen has a plain link to skip ahead. Every "
       "page works without JavaScript: the scripts add the live map reaction and the evaluation stages, but no content "
       "depends on them, and a visitor who prefers reduced motion gets no animation.")

# Chapter 6
rule_p("It also answers the question that follows a failed check.",
       "It also answers the question that follows a failed check. The Syllabus Map shows which topics of the BIT "
       "syllabus no past project has used, and the Ideas page ranks real problems from Nepal that are built from those "
       "topics, with a proof line for each. Past projects load by themselves from files dropped into a folder.")
rule_p("The database does part of the work.",
       "The database does part of the work. Triggers maintain counters and the audit trail, a stored procedure moves "
       "each semester's approved ideas into the corpus in one transaction, and a background thread keeps the "
       "suggestions current. All six specific objectives were met, as Section 5.2 shows, with all 84 automated tests "
       "passing. The new matching formula agrees with human labels as often as the old one, so its gain is in how "
       "clearly it explains a match and not in accuracy. The result is a tool that helps students and supervisors "
       "have a better conversation about ideas, earlier in the semester.")
rule_p("A typical session: sign in, open Check an idea",
       "A typical session: open the home page and type an idea after \"I want to build\"; the map shows how much of "
       "that ground is taken. Sign in, press Run the full check, complete the form and read the result after the "
       "evaluation screen. Edit and check again if the idea overlaps, then lock it for the group. The supervisor drops "
       "past-project files into the DB folder, reviews the locked ideas on the supervisor desk, and closes the "
       "semester when every group is approved.")

rule_p("The listings below are the parts most often asked about",
       "The listings below are the parts most often asked about: the engine loop, the weighted overlap, the folder "
       "watcher, the trigger that grows the problem bank, and the archive procedure.")

# ---------------------------------------------------------------- bullet rules

B = {}
B_APPEND = {}


def rule_b(prefix, new):
    B[prefix] = new


rule_b("To develop a gap map and an idea generator",
       "To develop a Syllabus Map and an idea suggester that turn real problems and syllabus topics into ranked project "
       "suggestions, each with its proof.")
rule_b("To implement Class Radar locking, supervisor review, semester archiving and a background decision engine",
       "To implement Class Radar locking, supervisor review, semester archiving and a DB folder that loads past "
       "projects from CSV, TXT, XLSX and DOCX files by itself.")
rule_b("Gap map and ideas: a domain by project-type map",
       "Syllabus Map and ideas: a live map of 41 syllabus topics shaded by how many past projects used each, a domain "
       "by project-type gap map built from database views, and ranked suggestions built from a bank of real problems "
       "and the syllabus topics they use.")
rule_b("Self-updating database: triggers for counters",
       "Self-updating database: triggers for counters and the audit trail, a stored procedure that archives each "
       "semester into the corpus, a folder watcher that loads past projects from CSV, TXT, XLSX and DOCX files, and a "
       "background thread that refreshes suggestions.")
B_APPEND["Checking finished reports or source code for copied text"] = [
    "Reading PDF files. They must be converted to DOCX or CSV first, because the JDK cannot read them and the "
    "project avoids libraries outside the syllabus.",
]
rule_b("It explains every score as tags in common, words in common and project type",
       "It explains every score as tags in common, words in common and project type, lets rare terms count more than "
       "common ones, tells each match as what is shared, what is new and what to change, and resists renaming through "
       "a synonym table and buzzword padding through a claim detector.")
rule_b("It answers the next question as well: a gap map shows",
       "It answers the next question as well: a Syllabus Map shows which topics of the syllabus no past project has "
       "used, and the Ideas page offers real problems from Nepal built from those topics, each with proof such as how "
       "many past projects used the same pair of topics.")
rule_b("A background decision engine refines the closest matches",
       "Past projects load themselves: files dropped into a folder are read within about ten seconds, rows already in "
       "the corpus are skipped, and the map and the suggestions update without anyone clicking.")
rule_b("**Back-end: Java Servlets on Apache Tomcat 10.1.**",
       "**Back-end: Java Servlets on Apache Tomcat 10.1.** Servlets are the syllabus way to write web applications in "
       "Java, and Tomcat is the container named in it. Nineteen servlets act as controllers, one filter handles "
       "security and a context listener starts the application [7], [8], [17].")
rule_b("**Front-end: JSP with JSTL.**",
       "**Front-end: JSP with JSTL.** Pages are rendered on the server, so they work without JavaScript. Two small "
       "scripts add the live Syllabus Map and the evaluation screen on top.")
rule_b("**Decision engine: TypeSafe Jev over HTTPS (optional).**",
       "**File reading: java.io and java.util.zip.** CSV and TXT files are read with BufferedReader. XLSX and DOCX "
       "files are zip archives of XML, so they are opened with the zip classes and the XML parser that come with the "
       "JDK, and no extra library is needed.")
rule_b("Total automated tests executed: 35",
       "Total automated tests executed: 84 (59 unit, 25 integration), plus scripted Chrome runs that typed, clicked, "
       "dropped files and captured 20 pages on desktop and phone, with no console error in any run.")
rule_b("Tests passed: 35 of 35",
       "Tests passed: 84 of 84, and every HTTP check returned the expected status.")
rule_b("Failed during development: 1.",
       "Failed during development: 3. First, the rename test scored 0.5 instead of at least 0.66 for tags, because "
       "plural tags such as \"books\" were not folded to their synonym; the fix folds a plural tag only when its "
       "singular is a known tag or synonym. Second, the new matching formula agreed with 19 of 25 labelled pairs "
       "against 22 for the old one; reducing the second overlap measure to 20% fixed it. Third, the first Syllabus Map "
       "had 37 of 41 plots open, because the demo abstracts say what a project does and not how it was built; storing "
       "the tech stack with each project gave 16 open plots.")
rule_b("Tools used: JUnit 5, Maven Surefire, a separate MySQL test database, curl and Playwright.",
       "Tools used: JUnit 5, Maven Surefire, separate MySQL test databases, curl and a Chrome DevTools script in Node.")
rule_b("A decision-engine question that could not decide.",
       "A formula that did worse than the old one. The first version of the new matching formula agreed with 19 of 25 "
       "labelled pairs against 22 for the old one, because counting how much of a small idea an old project already "
       "has pushed overlapping ideas into the Already done band. A sweep showed the cause and the share was cut to "
       "20%. Accuracy is now equal, not better.")
rule_b("A verdict that changed after a lock.",
       "A map that showed nothing. The first Syllabus Map had 37 of 41 plots hatched, because the demo abstracts say "
       "what a project does and not how it was built. We stored the tech stack with each project and read topics from "
       "it, which gave 16 open plots and a spread that matches what students actually build.")
rule_b("The corpus and problem bank are seed data prepared by the team.",
       "The corpus and problem bank are seed data prepared by the team, and the tech stacks of the 71 demo projects were "
       "written for the demonstration. The college's real archive has to be dropped into the DB folder before Origin "
       "can judge real proposals.")
rule_b("The live decision engine is an external paid service in early access.",
       "Topic detection is keyword matching, so it can miss a topic that a project used without naming it and can "
       "count one that it only mentions. The type counts 20% of a score, so a project of the same type that shares "
       "one tag can outrank a closer project of another type: the health-post idea in Figure 4.5 does not list the "
       "pharmacy desktop application among its five closest projects.")
B_APPEND["The corpus and problem bank are seed data prepared by the team."] = [
    "PDF files cannot be read. A real Excel file, a CSV and a Word table were each dropped into the running app "
    "and imported correctly.",
    "Every request opens its own database connection, which is simple but not the fastest design.",
    "An optional second opinion from an external decision engine exists in the code and is switched off, so the "
    "system runs on rules alone.",
]
rule_b("Import the college's real archive of past project titles",
       "Add an upload button to the supervisor desk and read PDF files, so the college's old reports can be loaded "
       "without conversion.")
B_APPEND["Import the college's real archive of past project titles"] = [
    "Switch the optional decision-engine second opinion back on and test it with a live key [11].",
    "Use a connection pool, which is outside the current syllabus, and measure how much time it saves.",
]
rule_b("Step 3: Configure the settings:",
       "Step 3: Configure the settings: copy config/origin.properties.example to config/origin.properties and enter the "
       "MySQL password.")
rule_b("Step 4: Set up the database:",
       "Step 4: Set up the database: nothing to run by hand. On first start Origin creates the origin database, the "
       "schema, the triggers, the procedures and the demo data. To add real past projects, copy CSV, TXT, XLSX or DOCX "
       "files into the DB folder next to run.bat; Origin reads them within about ten seconds.")

# ---------------------------------------------------------------- table rules


def table_2_2(headers, rows, widths):
    out = []
    for r in rows:
        if r[0] == "Functional - Gap map and ideas":
            out.append(["Functional - Syllabus Map and ideas",
                        "A live map of syllabus topics reacts to a typed idea; the Ideas page ranks suggestions with "
                        "proof and puts plain CRUD ideas last.", "High"])
            out.append(["Functional - Past-project import",
                        "Files dropped into the DB folder (CSV, TXT, XLSX, DOCX) are read within about ten seconds; "
                        "bad rows are skipped and reported.", "High"])
        elif r[0] == "Non-Functional - Reliability":
            out.append(["Non-Functional - Reliability",
                        "A bad or half-copied file never stops the system and never half-fills the corpus.", "Medium"])
        else:
            out.append(r)
    return headers, out, widths


def table_2_3(headers, rows, widths):
    return headers, [["Economic", "All tools are free, and no paid service is needed.", "Feasible"] if r[0] == "Economic" else r
                     for r in rows], widths


def table_3_1(headers, rows, widths):
    names = {"Increment 4: idea generator and background engine": "Increment 4: idea generator and DB-folder import",
             "Increment 5: supervisor desk": "Increment 5: Syllabus Map, suggester, supervisor desk"}
    return headers, [[names.get(r[0], r[0])] + list(r[1:]) for r in rows], widths


def table_4_1(headers, rows, widths):
    out = []
    for r in rows:
        if r[0] == "JSON":
            out.append(["JSON", "Jakarta JSON Processing (Eclipse Parsson 1.1)", "The live preview reply and the idea data on the home page"])
        elif r[0] == "Testing":
            out.append(["Testing", "JUnit 5, a Chrome DevTools driver in Node, Playwright", "Automated tests, browser checks and diagrams"])
        else:
            out.append(r)
    return headers, out, widths


def table_4_2(headers, rows, widths):
    new = [
        ["Term weight", "1 / (1 + ln(1 + n)), n = past projects that use the term", "Rare terms count more"],
        ["Overlap", "80% weighted Jaccard index + 20% share of the idea's weight already present", "Used for tags and words"],
        ["Tags in common", "Overlap of canonical tag sets", "45% of the score"],
        ["Words in common", "Overlap of title and description keywords", "35% of the score"],
        ["Same type", "1 if both are the same project type; counted only when tags or words overlap", "20% of the score"],
        ["Looks original", "Final score below 0.35", "Band ORIGINAL"],
        ["Overlaps", "Final score from 0.35 to 0.59", "Band SIMILAR"],
        ["Already done", "Final score of 0.60 or more", "Band DUPLICATE"],
        ["Not enough data", "Fewer than 15 projects in the corpus", "Band LOW_CONFIDENCE"],
    ]
    return headers, new, widths


def table_4_3(headers, rows, widths):
    return headers, list(rows) + [
        ["ingest_log", "Table", "Remembers which version of which DB-folder file has been read, so an unchanged file is never read twice"],
        ["syllabus_topics, corpus_project_topics, problem_topics", "Tables", "Hold the 41 topics and link past projects and problems to them; the map and the proof lines are counted from these"],
    ], widths


def table_4_4(headers, rows, widths):
    out = []
    for r in rows:
        r = list(r)
        if r[0] == "TC-07":
            r[4] = "DUPLICATE (62%); top match Online Library Book Lending Portal"
        elif r[0] == "TC-12":
            r[1] = "Background refinement (optional engine, simulated)"
        elif r[0] == "TC-20":
            r[2] = "25 labelled idea pairs"
            r[3] = "New formula agrees at least as often as the old one"
            r[4] = "22 of 25 (old formula: 22 of 25)"
        elif r[0] == "TC-21":
            r[1] = "Lock before the background check finishes (optional engine, simulated)"
        out.append(r)
    out += [
        ["TC-22", "Rapid typing on the home page", "A 60-character idea typed key by key", "One request to /peek", "1 request", "Pass", "System"],
        ["TC-23", "Map reacts to a typed idea", "A library idea with fines and reminders, then a Banker's algorithm idea", "Worked ground for the first, open ground for the second", "68% alike, Already done, 5 plots red; then 100% original, 1 plot ringed", "Pass", "System"],
        ["TC-24", "Real Excel file dropped in the DB folder", "batch-2024.xlsx written by Excel, 3 projects", "Projects appear within about 10 seconds", "3 projects added, visible after 8.0 seconds", "Pass", "System"],
        ["TC-25", "CSV with a duplicate and a bad row", "3 rows: new, existing title and year, no year", "1 added, 2 skipped with reasons", "1 added; 1 already in the corpus; line 4 has no valid year", "Pass", "System"],
        ["TC-26", "File still being copied", "A file that grows between scans", "Not read until its size stops changing", "Read on the third scan", "Pass", "Integration"],
        ["TC-27", "Hostile workbook", "XLSX whose XML declares an external entity", "Entity not expanded", "No secret text in the result", "Pass", "Unit"],
        ["TC-28", "Proof matches a hand count", "All 84 suggestions", "Pair count equals a SQL count of projects using both topics", "84 of 84 equal", "Pass", "Integration"],
        ["TC-29", "Plain CRUD ranked last", "Suggestions whose topics are only JDBC, Servlet or ER design", "Never listed above a real idea", "No real idea listed after a CRUD one", "Pass", "Integration"],
        ["TC-30", "Evaluation screen", "Submit a check", "Five stages with real counts, then the result", "Stages run in about 2.4 s; result opens about 2.9 s after the screen appears; reduced motion goes straight to the result", "Pass", "System"],
        ["TC-31", "Live preview throttle", "40 calls from one address in a few seconds", "Calls beyond 30 refused", "30 answered, 10 refused with HTTP 429", "Pass", "Security"],
        ["TC-32", "Phone width", "Home, Ideas, History and Radar at 390 px", "No sideways page scroll", "None", "Pass", "System"],
    ]
    return headers, out, widths


def table_5_1(headers, rows, widths):
    return headers, [
        ["Landing page with the Syllabus Map", "376 ms"],
        ["Live preview while typing (/peek)", "66 ms"],
        ["Ideas page (first 15 of 42)", "314 ms"],
        ["Class Radar", "149 ms"],
        ["Check form", "273 ms"],
        ["Submit a check (validate, score, save in one transaction)", "328 ms"],
        ["Evaluation screen", "148 ms"],
        ["Result page with matches told as stories", "272 ms"],
    ], widths


TABLE_RULES = {"2.2": table_2_2, "2.3": table_2_3, "3.1": table_3_1, "4.1": table_4_1, "4.2": table_4_2,
               "4.3": table_4_3, "4.4": table_4_4, "5.1": table_5_1}

# ---------------------------------------------------------------- figures: old number -> (new number or None to drop, new title or None)

FIG = {
    "4.3": ("4.4", None), "4.4": ("4.5", None), "4.5": ("4.6", None), "4.6": ("4.7", None),
    "4.7": (None, None),          # the ideas figure moves into 4.2.6
    "4.8": ("4.11", None),
    "5.1": ("5.1", "Screenshot - Landing Page with the Syllabus Map"),
}

# ---------------------------------------------------------------- headings

H3 = {}


def new_sections(w, O, IMG):
    O.h3(w, "4.2.6 Syllabus Map and Idea Suggester Module")
    O.p(w, "SyllabusService describes the syllabus as 41 topics (Java 10, Database 9, Operating Systems 9, Computer "
           "Organization 6, Statistics 7), each with a course, a unit and a few keywords. TopicTagger links a past "
           "project to every topic whose keywords appear in its title, description or tech stack. It reads two and "
           "three neighbouring words as one, so \"round robin\" matches the keyword round-robin, and it avoids everyday "
           "words such as \"scan\" or \"view\" that would mark ordinary projects. With the 71 demo projects, 16 of the "
           "41 topics were never used. JDBC appears in 66 projects, Servlet and JSP in 40 and Swing in 15, while 8 of "
           "the 9 Operating Systems topics are open. The tech stacks of the demo projects were written for the "
           "demonstration.")
    O.p(w, "The home page turns these counts into the Syllabus Map. Each plot is a topic, shaded by how many past "
           "projects used it and hatched when none did. As a visitor types, the page asks the /peek servlet about the "
           "text after a 300 millisecond pause, so a burst of typing makes one request. The servlet scores the text "
           "against the past projects through MatchIndex and returns the originality number, the closest project, the "
           "topics the text mentions and the topics of the close matches. Plots on worked ground turn red, topics the "
           "idea uses get an ochre ring, and the colour travels across the map as a wave. A library idea reads Already "
           "done at 68% alike and turns five plots red (Figure 5.1). An idea built on the Banker's algorithm reads "
           "Looks original, and only the Deadlock plot is ringed (Figure 4.8).")
    O.figure(w, "4.8", IMG / "f55-map-open.png", "Screenshot - Syllabus Map on Open Ground", 15.0, 11)
    O.p(w, "Choosing a plot filters the ideas below the map to those built on that topic (Figure 4.9), and Surprise me "
           "lights the topics of one strong idea. The page works without JavaScript: the map, the first ideas and the "
           "links are ordinary HTML.")
    O.figure(w, "4.9", IMG / "f57-map-filter.png", "Screenshot - Plot Filter and the Idea Built on It", 15.0, 17)
    O.p(w, "IdeaService pairs each of the 42 problems in the problem bank with its suitable project types, which gives "
           "84 suggestions, and with two to four syllabus topics and a one-line twist written for it, such as using the "
           "Banker's algorithm to share blankets between shelters. A suggestion's strength is 0.40 x untouched + 0.30 x "
           "coverage + 0.20 x feasibility + 0.10 x impact. Untouched is 1 / (1 + n), where n is how many past "
           "projects used the idea's busiest pair of topics together. Coverage is the number of syllabus units the "
           "idea uses out of four. Feasibility comes from the project type's effort and impact from the problem's "
           "source. An idea whose topics are all everyday plumbing (JDBC, Servlet and JSP, ER design) is flagged as "
           "plain CRUD and ranked after every other idea. An idea 60% or more like a past project is retired, and one "
           "that a locked idea already matches at 45% or more is marked as taken by that group. Every suggestion "
           "shows its proof, for example \"0 of 71 past projects used Descriptive statistics + Multithreading "
           "together. Covers 4 syllabus units across 3 courses.\" Of the 84 suggestions, 77 are open and 7 are taken. "
           "Figure 4.10 shows the Ideas page.")
    O.figure(w, "4.10", IMG / "f46-ideas.png", "Screenshot - Ranked Project Ideas with Proof", 15.0, 18)
    O.h3(w, "4.2.7 DB Folder Import Module")
    O.p(w, "A watcher thread looks in the DB folder every five seconds. A file is read once its size and time have "
           "stayed the same for one scan, so a file that is still being copied is never half-read, and a changed file "
           "is read again. TableReader picks a reader by the file extension: DelimitedReader for CSV and TXT, which "
           "finds the separator in the header line and handles quoted cells, and XlsxReader and DocxReader for the "
           "zip-based formats. The XML parsers have DTDs and external entities switched off, so a hostile file cannot "
           "read other files from the server. HeaderMap finds columns by header name, ignoring case and spacing, so "
           "\"Project Name\", \"project_name\" and \"Title\" are the same column.")
    O.p(w, "IngestService adds all the new rows of a file in one transaction: either every row goes in or none does. "
           "Rows already in the corpus (same title and year) are skipped, and a row without a title or a valid year "
           "is skipped with its line number. A missing domain or type is worked out from the text by Classifier, and "
           "missing tags from the vocabulary. Each file version gets one line in ingest_log, which the supervisor page "
           "shows. A file the system cannot read, such as a PDF, is listed as unsupported with the advice to convert "
           "it. In a test with a real Excel file and a CSV, both were read 8.0 seconds after being copied: the "
           "workbook added 3 projects, and the CSV added 1, skipped 1 duplicate and reported a row with no year by "
           "its line number.")
    O.h3(w, "4.2.8 Supervisor Desk and Self-Updating Database")


H3["4.2.5 Gap Map and Idea Generator Module"] = ("rename", "4.2.5 Gap Map Module")
H3["4.2.6 Background Decision Engine Module"] = ("insert", new_sections)
H3["4.2.7 Supervisor Desk and Self-Updating Database"] = ("skip", None)      # new_sections writes the renumbered heading

LABELS = {
    "Objective 4: To develop a gap map and an idea generator.": "Objective 4: To develop a Syllabus Map and an idea suggester.",
    "Objective 5: To implement Class Radar, supervisor review, semester archiving and background refinement.":
        "Objective 5: To implement Class Radar, supervisor review, semester archiving and the DB folder.",
}

# ---------------------------------------------------------------- code listings

COMPARE = [
    "public Breakdown compare(Candidate idea, Candidate other) {",
    "    double t = tags.score(idea, other);",
    "    double k = keywords.score(idea, other);",
    "    double y = type.score(idea, other);",
    "    double total = tags.weight() * t + keywords.weight() * k;",
    "    // the type counts only when tags or words already overlap",
    "    if (t > 0 || k > 0) total += type.weight() * y;",
    "    return new Breakdown(other, t, k, y, total);",
    "}",
]
OVERLAP = [
    "public static double of(Set<String> mine, Set<String> theirs,",
    "                        TermStats stats) {",
    "    Set<String> shared = new HashSet<>(mine);",
    "    shared.retainAll(theirs);",
    "    if (shared.isEmpty()) return 0.0;",
    "    Set<String> union = new HashSet<>(mine);",
    "    union.addAll(theirs);",
    "    double s = stats.weight(shared);",
    "    return 0.8 * (s / stats.weight(union))",
    "         + 0.2 * (s / stats.weight(mine));",
    "}",
]
SCAN = [
    "public synchronized int scan() throws IOException {",
    "    for (Path file : filesIn(folder)) {",
    "        String version = size(file) + \":\" + modified(file);",
    "        // read only when unchanged since the previous scan",
    "        boolean stable = version.equals(lastSeen.put(name, version));",
    "        if (stable && !log.seen(name, size, modified)) {",
    "            service.ingest(file);   // one transaction per file",
    "        }",
    "    }",
    "}",
]
SCORER = [
    "public interface Scorer {",
    "    String name();",
    "    double weight();         // all weights add up to 1.0",
    "    double score(Candidate idea, Candidate other); // 0 to 1",
    "}",
    "",
    "public class TagScorer implements Scorer {",
    "    private final TermStats stats;",
    "    public double weight() { return 0.45; }",
    "    public double score(Candidate idea, Candidate other) {",
    "        return Overlap.of(idea.tags(), other.tags(), stats);",
    "    }",
    "}",
]


def after_archive_code(w, O):
    O.label(w, "A.4 Overlap.of: weighted overlap of two term sets (Java)")
    O.code(w, OVERLAP)
    O.label(w, "A.5 DbFolderWatcher.scan: read a file once it stops changing (Java, abridged)")
    O.code(w, SCAN)


# ---------------------------------------------------------------- installation

def install(br):
    W = br.Writer
    IMG = br.IMG
    O = type("Originals", (), {})()
    for name in ("p", "h2", "h3", "label", "bullets", "figure", "table", "code"):
        setattr(O, name, getattr(W, name))
    state = {"skip_h3_after": False}

    def used(kind, key):
        USED[(kind, key)] += 1

    def p(self, text, *a, **k):
        for prefix, new in P.items():
            if text.startswith(prefix):
                used("P", prefix)
                if prefix == "CheckService validates the input, builds the Candidate":
                    evaluation_and_rename(self, O, IMG)
                    return None
                if new is None:
                    return None
                text = new
                break
        orig = text
        para = O.p(self, text, *a, **k)
        return para

    def p_with_after(self, text, *a, **k):
        original = text
        para = p(self, text, *a, **k)
        for prefix, fn in AFTER_P.items():
            if original.startswith(prefix) and fn is not None:
                used("AFTER", prefix)
                fn(self, O)
        return para

    def bullets(self, items):
        out = []
        extra = []
        for it in items:
            replaced = False
            for prefix, new in B.items():
                if it.startswith(prefix):
                    used("B", prefix)
                    out.append(new)
                    replaced = True
                    break
            if not replaced:
                out.append(it)
            for prefix, adds in B_APPEND.items():
                if it.startswith(prefix):
                    used("BAPP", prefix)
                    extra += adds
        return O.bullets(self, out + extra)

    def table(self, number, title, headers, rows, widths, *a, **k):
        fn = TABLE_RULES.get(number)
        if fn:
            used("T", number)
            headers, rows, widths = fn(headers, rows, widths)
        return O.table(self, number, title, headers, rows, widths, *a, **k)

    def figure(self, number, path, title, width_cm=15.0, max_height_cm=None):
        if number in FIG:
            used("F", number)
            new_number, new_title = FIG[number]
            if new_number is None:
                return None
            number = new_number
            title = new_title or title
        return O.figure(self, number, path, title, width_cm, max_height_cm)

    def h3(self, text):
        rule = H3.get(text)
        if rule:
            used("H3", text)
            kind, arg = rule
            if kind == "rename":
                return O.h3(self, arg)
            if kind == "insert":
                arg(self, O, IMG)
                return None
            if kind == "skip":
                return None
        return O.h3(self, text)

    def label(self, text):
        if text in LABELS:
            used("L", text)
            text = LABELS[text]
        return O.label(self, text)

    def code(self, lines):
        first = lines[0] if lines else ""
        if first.startswith("public interface Scorer"):
            used("C", "scorer")
            return O.code(self, SCORER)
        if first.startswith("public Breakdown compare"):
            used("C", "compare")
            return O.code(self, COMPARE)
        result = O.code(self, lines)
        if first.startswith("CREATE PROCEDURE sp_archive_semester"):
            used("C", "archive")
            after_archive_code(self, O)
        return result

    W.p = p_with_after
    W.bullets = bullets
    W.table = table
    W.figure = figure
    W.h3 = h3
    W.label = label
    W.code = code

    br.ABSTRACT = ABSTRACT
    br.KEYWORDS = KEYWORDS
    br.ABBREVIATIONS = sorted(list(br.ABBREVIATIONS) + EXTRA_ABBREVIATIONS, key=lambda x: x[0].lower())

    # the 4.2.3 "check form" paragraph has no follow-up of its own: its content comes from the replaced paragraph
    AFTER_P.pop("The check form asks for a title", None)


def verify():
    expected = []
    expected += [("P", k) for k, v in P.items()]
    expected += [("AFTER", k) for k in AFTER_P]
    expected += [("B", k) for k in B]
    expected += [("BAPP", k) for k in B_APPEND]
    expected += [("T", k) for k in TABLE_RULES]
    expected += [("F", k) for k in FIG]
    expected += [("H3", k) for k in H3]
    expected += [("L", k) for k in LABELS]
    expected += [("C", k) for k in ("scorer", "compare", "archive")]
    missing = [e for e in expected if USED[e] == 0]
    if missing:
        raise SystemExit("report_v3: rules that never matched:\n  " + "\n  ".join(f"{k}: {v[:90]}" for k, v in missing))
    print("report_v3: all", len(expected), "rules matched")
