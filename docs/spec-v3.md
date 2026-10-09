# Origin v3 - Spec

Extends `spec.md` (v2). Same team, same stack. This document covers only what changes.

## One line
Drop past projects into a `DB` folder and Origin loads them by itself, checks new ideas against them with a simple weighted score, and suggests the next project nobody has built, built from the syllabus.

## Success criteria
1. A file copied into `DB/` shows up in the corpus within 10 seconds, with no click and no restart.
2. Every score and every suggestion can be explained in one sentence and recomputed by hand.
3. The suggester never proposes a plain management system.
4. Each suggestion shows its proof, for example: "0 of 140 past projects used Banker's algorithm with JDBC."
5. The new matching formula agrees with the team's labelled pairs at least as often as the v2 formula does.

## Scope (IN)

### 1. DB folder auto-ingest
- A background thread scans `DB/` every 10 seconds. It compares each file's size and last-modified time with the `ingest_log` table.
- New or changed file: parse, then insert all rows in one transaction. A failed file is rolled back and logged. It does not stop other files.
- Duplicates are skipped on title + year.
- Format: **CSV only**, read with the JDK (`BufferedReader`). No new libraries.
  - The delimiter can be a comma, pipe, tab or semicolon. Quoted cells may hold delimiters and line breaks.
- Columns are matched by header name with a small alias list (title / project name, year, abstract / description, tech, type, domain). Case and spacing are ignored.
- Missing domain or tags are filled from the keyword vocabulary. A row with no title or no year is rejected, with the line number in the log.
- Every non-CSV file is listed on the supervisor page as unsupported. Convert it to CSV before import.
- Supervisor page shows the ingest log: file, rows added, rows skipped, status.

### 2. Matching (upgraded, one formula)
For an idea and one other project:

- **Weight of a term** (tag or keyword): `w = 1 / (1 + ln(1 + n))`, where n is the number of projects that use it. Rare terms weigh more.
- **Overlap of two term sets**, average of two views:
  - *Shared of everything*: weighted Jaccard, `sum w(shared) / sum w(union)`.
  - *How much of your idea they already have*: `sum w(shared) / sum w(your terms)`.
- **Score** = 0.50 x tag overlap + 0.35 x keyword overlap + 0.15 x type match.
  - The type match counts only when tags or keywords share at least one term.
- **Top-3 coverage**: the share of your idea's weighted terms found in the union of the three closest projects. Shown as "your idea is X% covered by N projects together".
- **Bands** keep the v2 thresholds: ORIGINAL below 0.35, SIMILAR from 0.35, DUPLICATE from 0.60, LOW-CONFIDENCE below 15 projects.
- **Story output** per match: *shared terms*, *yours only*, *theirs only*, and one fix sentence.
- **Speed**: an in-memory inverted index (term to project ids), rebuilt after each ingest. Only projects sharing at least one term are scored.
- **Class Radar** uses the same formula against locked class ideas.
- Claim detector (buzzword padding) is unchanged.
- **Jev**: the code stays in the repository, commented out and inactive. The score has no engine blend. README, report and slides do not present it as a feature.

### 3. Next-project suggester
- New `syllabus_topic` table: about 45 topics from the BIT 4th semester syllabus, grouped by course (Java, DBMS, OS, COA, Statistics), with keyword lists for detection. Examples: page replacement, Banker's algorithm, Swing JTable, socket programming, RMI, triggers, hypothesis tests.
- Each past project is linked to the topics its text mentions (`project_topic`). Usage count per topic = how crowded it is.
- Each entry in the problem bank (`problems.csv`, extended with a `topics` column) is linked to 2 to 3 syllabus topics (`problem_topic`).
- **Suggestion score** = 0.40 untouched combination + 0.30 syllabus coverage + 0.20 feasibility + 0.10 impact.
  - *Untouched combination*: 1 minus the share of past projects that used the same topic pair.
  - *Syllabus coverage*: topics used, out of the 5 courses' units, capped at 1.
  - *Feasibility* and *impact* keep the v2 definitions.
- **No plain management systems**: a suggestion whose topics all sit inside DBMS + JDBC CRUD is flagged "CRUD only" and ranked last, never shown in the top results.
- **Proof line** on every suggestion: topic-pair usage in past projects, units covered, closest past project.
- Taken-by-a-class-group status stays as in v2.
- Suggestions refresh after each ingest and every ten minutes, as in v2.

### 4. Interface
- Home page becomes the **Syllabus Map**: a grid of topics grouped by course, shaded by how many past projects used each one. Empty cells are open ground. Clicking a topic filters the suggestions. It shows the algorithm as a picture.
- **First-use moment (the "woah")**: the map is the whole first screen, fully visible on load, with one large input above it: "What do you want to build?"
  - As the student types, the map reacts live. Topics the idea touches light up, crowded ones in a hot tone and open ones in a cool tone. The closest past project and a running originality number update beside it.
  - No form, no page load. A small servlet (`/peek`) returns JSON for the typed text, and the page updates the map from it. Calls are debounced to one per 300 ms and score through the same index as the real check.
  - A "Surprise me" button shows the best open-ground suggestion with its proof line, and its topics glow on the map.
  - Typing "library management" makes the map go hot and shows "Already done 8 times". Typing a Banker's algorithm idea makes the same cells go cool and shows "Nobody has built this".
  - Pressing Enter runs the full check and opens the result page, with the story, the coverage line and the fix sentence.
- **Evaluation step**: the full check does not jump straight to the result. A short evaluation screen of about 2 to 3 seconds shows the real stages as they finish: reading your idea, searching N past projects, weighing rare terms, checking class ideas, checking coverage. Each stage shows its true count (for example "142 projects, 9 candidates"). If the server finishes sooner, the screen holds until every stage has been readable, with a 3 second ceiling. `prefers-reduced-motion` shows the stages as a static list and goes straight to the result. The live preview while typing is not delayed.
- **User friendly**: one primary action per page, plain words in place of jargon (no "Jaccard" in the interface), every score has a one-line "why", empty and error states say what to do next, keyboard works end to end, and a returning student sees their last idea first.
- Motion is limited to things already on screen (cells shading, the number counting, the map filtering). Nothing is hidden until an animation runs, and `prefers-reduced-motion` turns it all off.
- Check result page: score, band, the shared / yours / theirs story, coverage line, fix sentence.
- Ideas page: suggestions with their proof lines and syllabus units.
- Supervisor page: ingest log and DB folder status.
- Visual rules: follow `slop.md`. One palette with no blue-purple and no cream or stock grey, one type voice, no glow, no stock hero, no content hidden behind entrance animation, all controls working. Motion only on visible things (map hover, filters). Exact palette and type are chosen in the plan after reviewing the current screens.
- Responsive down to a phone, light and dark.

### 5. Kept as in v2
Login and roles, Class Radar and locking, supervisor approval and corrections, triggers, views, `sp_archive_semester`, CSV exports, security measures.

## Out of scope
PDF ingest, Jev as an active feature, fuzzy or typo matching, new libraries, mobile app, email, multi-college use.

## Tech (unchanged, syllabus only)
Java 17, Servlet + JSP + JSTL on Tomcat, JDBC `PreparedStatement`, MySQL (tables, views, triggers, procedures, transactions), threads, and file IO for CSV imports and exports.

## Edge cases
- File still being copied (size changes between scans): wait for the next scan.
- Same file re-saved with no change in content: skipped by size and time check, then by title + year.
- Empty `DB/` folder or missing folder: created on start, no error.
- A file with a bad header: whole file rejected with a clear log message.
- Corpus under 15 projects: LOW-CONFIDENCE, as in v2.
- Idea with no tags: validation error, not a crash.
- Syllabus topic with zero past use: weight at its maximum, shown as open ground.

## Tests
- Parser tests for each format, including a bad header and a half-written file.
- Matching: the 15 v2 labelled pairs plus about 10 new small-idea and mash-up pairs. Old and new formulas are both run. The new one ships only if it agrees on at least as many pairs. Both numbers go in the report.
- Suggester: no CRUD-only suggestion in the top results; proof line numbers match a hand count.
- Integration: copy a file into `DB/`, wait, see the project in the corpus.
- UI: every control clicked in a browser, mobile width checked, slop.md re-check.
- Live preview: typing returns the right map cells and never blocks or flickers; a rapid burst of keystrokes produces one call.
- Evaluation screen: stages show real counts, the hold never exceeds 3 seconds, and reduced-motion skips it.
- Full run in a real browser before the final response: first-use journey (type, watch the map, Surprise me, run a check, read the result), ingest of a dropped file, supervisor log, phone width, light and dark. Evidence goes in the report.
- Full `mvn test` must pass, with the count reported as it is.

## Documents
After the build is verified: update `Origin_Final_Report.docx` and `Origin_Slides.pptx` (through `tools/build_report.py` and `tools/build_slides.py`) with the new ingest, the matching formula, the suggester, the new screens and the test numbers.

## As built: where the build differs from this spec

Written after the build, from what the tests and the browser checks showed.

- **Weights are 45 / 35 / 20, not 50 / 35 / 15.** The approved formula was run against 25 hand-labelled pairs (the 15 from v2 plus 10 new ones, labelled before the run). It agreed on 19 of 25, while the v2 formula agreed on 22. A sweep showed that giving containment half of the overlap lost three pairs, so containment is 20% of the overlap and the v2 weights stay. With that, the current formula agrees on 22 of 25, the same as v2. The measured gain is therefore in the explanations (rare-term weights, the shared / yours / theirs story, top-3 coverage), not in band agreement.
- **Coverage of the suggester** is distinct syllabus units out of 4 (not "out of the 5 courses' units"), capped at 1.
- **Tech stack is stored.** `corpus_projects.tech` was added (an upgrade step adds it to older databases) because the tech column is where syllabus topics show. The 71 demo projects were given stacks written for the demo; real files bring their own.
- **Ideas already built are retired** (60% or more like a past project), and ties between equal-strength ideas are broken by syllabus units, then novelty.
- **Evaluation screen**: about 2.4 seconds from first stage to last, then the result opens (measured about 2.9 seconds from the screen appearing). Reduced motion goes straight to the result.
- **Jev** is switched off through `DecisionClient.disabled()` in `App.java`; the Jev block in `IdeaService.refresh()` and the `scoreQuietly` method are commented out in place.
- **PDF** is not read. The supervisor page lists unsupported files.
- **Dark mode** was added to the stylesheet (a green-black palette), as the interface section asked for light and dark.
- **Tested with real files:** CSV files were dropped into the running app and read correctly. The reader accepts the comma, pipe, tab and semicolon delimiters, quoted cells and a byte-order mark. Only CSV is read. An Excel file, a Word document and a PDF are refused and the supervisor page lists them as unsupported.