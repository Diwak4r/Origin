# Origin

Origin checks a student project idea before a group spends a semester on it. It compares the idea with every past project and with the ideas other groups have locked this semester, shows which topics of the BIT syllabus nobody has built on, and suggests real problems that no one has solved yet, each one built from syllabus topics.

BIT Project-IV (BIT256CO), Purbanchal University. Sandesh Dotel, Bhumika Karki and Diwakar Ray Yadav, Himalayan Whitehouse International College.

## What it does

- **Syllabus Map.** The home page is one prompt, "I want to build", over a map of 41 topics from Java, Database, Operating Systems, Computer Organization and Statistics. As you type, the map reacts: plots on worked ground turn red, topics your idea uses get a ring, and a number says how original the idea looks.
- **DB folder.** Drop past projects into the `DB` folder as CSV files. Origin reads each file by itself within about ten seconds, skips rows it already has, and says what it did on the supervisor page.
- **Check an idea.** Title, description, tags, domain and type go in. A short evaluation screen shows what Origin looked at, then the result tells the match as a story: what you share with each past project, what only you have, what only they have, and one sentence on what to change.
- **Catch renames and padding.** Synonyms fold "books" and "lending" into `library`. Words like "AI" or "prediction" are flagged unless the description says how they work.
- **Next-project suggester.** 42 real problems from Nepal, each tied to 2 to 4 syllabus topics and a one-line twist. Each idea shows its proof, for example "0 of 71 past projects used these two topics together". Plain CRUD ideas rank last.
- **Class radar.** Each group locks one idea per semester. A new lock is compared with every other locked idea and clashes show up for both groups.
- **Supervisor desk.** Approve or send back locked ideas, correct a wrong match (Origin remembers the correction), download the semester report, read the DB folder log, and close the semester.

## How the score works

An idea and one past project are compared on three signals:

| Signal | Weight | How |
|---|---|---|
| Tags in common | 45% | weighted overlap of the canonical tags |
| Words in common | 35% | weighted overlap of the meaningful words |
| Same type | 20% | counts only when the tags or words already overlap |

- **Rare terms count more.** Each term weighs `1 / (1 + ln(1 + n))`, where n is the number of past projects that use it.
- **Overlap** is 80% "how much of everything do we share" and 20% "how much of my idea do they already have".
- **Coverage** shows how much of your idea the three closest projects cover together, which catches a mash-up of two old projects.
- Bands: below 35% looks original, 35% to 59% overlaps, 60% and above is already done.

## How the suggester scores an idea

strength = 0.40 untouched + 0.30 coverage + 0.20 feasibility + 0.10 impact

- **Untouched**: `1 / (1 + n)`, where n is how many past projects used the idea's busiest topic pair together.
- **Coverage**: distinct syllabus units the idea uses, out of 4, capped at 1.
- **Feasibility** and **impact** come from the project type's effort and the problem's source.
- An idea already 60% or more like a past project is retired. One a group has locked this semester is marked taken.

## The database does part of the work

| Object | What it does |
|---|---|
| `trg_cpt_count`, `trg_ict_count` | Keep per-tag counts for the corpus and for checks |
| `trg_check_problem` | Copies a student's problem statement into the problem bank |
| `trg_proposal_locked`, `trg_proposal_reviewed`, `trg_collision_audit` | Write the audit trail |
| `v_gap_matrix`, `v_domain_saturation`, `v_class_radar`, `v_check_history` | Views the pages read from |
| `fn_band` | Turns a score into ORIGINAL / SIMILAR / DUPLICATE, same thresholds as the Java code |
| `sp_archive_semester` | One transaction: approved ideas join the corpus, the semester closes, a new one opens |
| `sp_semester_report` | The supervisor's CSV report |
| `ingest_log` | Which version of which DB-folder file has been read |
| `syllabus_topics`, `corpus_project_topics`, `problem_topics` | The syllabus layer behind the map and the suggester |

Background threads do the rest: one scans the `DB` folder every five seconds, another refreshes the idea suggestions every ten minutes and after every lock or ingest.

## Run it

You need JDK 17 or newer, Maven 3.9, and MySQL 8 running on port 3306.

1. Copy `config/origin.properties.example` to `config/origin.properties` and put your MySQL password in it.
2. Double-click `run.bat`, or run it from a terminal in this folder.
3. Open http://localhost:8080.

The first start creates the `origin` database, the schema and the demo data. It takes about 20 seconds. A database made by an earlier version is upgraded on its next start.

Demo accounts, all with the password `Origin@2026`:

| Email | Role |
|---|---|
| `sandesh@origin.edu`, `bhumika@origin.edu`, `diwakar@origin.edu` | Students in group G19 (no idea locked at first start) |
| `kabin.bagale@origin.edu` ... `samir.karki@origin.edu` | The 18 BIT groups G01 to G18, one idea locked per group. `seed/class_proposals.csv` holds names, titles and emails. |
| `supervisor@origin.edu` | Supervisor |

To start over with fresh demo data, run `DROP DATABASE origin;` in MySQL and start Origin again.

To deploy on a standalone Tomcat 10.1 instead, run `mvn package` and copy `target/origin.war` into Tomcat's `webapps` folder.

## Adding past projects (the DB folder)

Put files in the `DB` folder next to `run.bat`. Origin creates the folder if it is missing.

- **Format:** CSV only (comma, pipe, tab or semicolon separated). Convert every other file type to CSV first. The supervisor page lists any file it cannot read.
- **Columns** are found by header name, ignoring case and spacing. Required: a title (`Title`, `Project Name`) and a year (`Year`, `Batch`). Optional: `Abstract` or `Description`, `Tech` or `Tech Stack`, `Type`, `Domain`, `Tags`.
- **Missing domain, type or tags** are worked out from the text. The tech stack is how Origin sees which syllabus topics a project used, so include it when you can.
- A file is read once its size stops changing, so a file that is still being copied is never half-read. A changed file is read again. Rows already in the corpus (same title and year) are skipped, and a row with no title or year is skipped with its line number in the log.

## The background decision engine (switched off)

Origin has code for a second opinion from TypeSafe's Jev model. It is switched off: `App.java` uses a disabled client and the Jev block in `IdeaService.refresh()` is commented out, so the app runs on rules alone and never makes a network call. The scoring in this README is the whole story. To turn it back on, follow the comments in `App.java`, restore the imports and the `scoreQuietly` method in `IdeaService`, and put a key in `config/origin.properties`.

## Tests

```
mvn test
```

81 tests. The integration tests build separate `origin_test` and `origin_ingest_test` databases, so they never touch the demo data. They are skipped if MySQL is not running. The engine test checks the formula against 25 pairs labelled by hand before the formula was run: the original 15 plus 10 that stress small ideas, renames and name-only collisions. The v2 formula agreed with 22 of the 25 and the current one agrees with 22 as well.

A browser check lives in `tools/qa` (needs Chrome and Node 22 or newer): `restart.ps1` starts the app, and the `.mjs` scripts type into the real pages, drop real files into `DB`, and record console errors and screenshots.

## Syllabus map

| BIT255CO / BIT254CO topic | Where it is used |
|---|---|
| Classes, inheritance, polymorphism, interfaces | `Scorer` interface with `TagScorer`, `KeywordScorer`, `TypeScorer`; the engine loops over `List<Scorer>` |
| Collections | `Set` and `Map` for term weights, `HashMap` inverted index in `MatchIndex`, `List` of matches |
| User-defined exceptions | `ValidationException`, `NotFoundException`, `EngineException` |
| Multithreading | Folder watcher thread, scheduled maintenance thread, worker pool, `synchronized` refresh |
| Java IO | `BufferedReader` for the seed CSVs and the DB-folder CSVs, CSV export |
| JDBC | `PreparedStatement` everywhere, transactions with commit and rollback, `CallableStatement` for procedures |
| Servlet and JSP | Servlets for pages and the `/peek` JSON endpoint, JSP pages with JSTL, a filter and a context listener on Apache Tomcat |
| ER model and normalisation | 25 tables in 3NF with foreign keys, unique and check constraints |
| SQL | Joins, nested queries, aggregates, views, triggers, a stored function, stored procedures |
| Security | Salted PBKDF2 passwords, role checks, CSRF tokens, login throttling, a throttle on the public `/peek` endpoint, security headers, XML parsers with external entities switched off |
| Transactions | Idea check, lock, semester archive and each file ingest are all-or-nothing |
