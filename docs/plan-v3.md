# Origin v3 - Plan and Tasks

Spec: `docs/spec-v3.md`. Execution: native, in order, verify after each task.

## Global constraints
- Java 17, Servlet/JSP/JSTL on Tomcat, JDBC `PreparedStatement`, MySQL. No new libraries. Past-project imports accept CSV only.
- Package base `np.edu.origin`. Follow existing style: small classes, DAO per concern, Javadoc on why.
- No em dashes in any user-facing text. Jev code stays, commented out.
- Tests need MySQL on 3306 (integration tests use `origin_test`).

## Architecture
- `engine/`: `TermStats` (term use counts, weight `1/(1+ln(1+n))`), `Overlap` (weighted Jaccard + containment average), upgraded `TagScorer`/`KeywordScorer`/`TypeScorer`, `SimilarityEngine` (weights 0.50/0.35/0.15, type gated), `Coverage` (top-3), `Story` (shared / yours only / theirs only), `MatchIndex` (inverted index).
- `ingest/`: `DbFolderWatcher` (scheduled thread, 10 s) -> `FileReader` per format -> `HeaderMap` -> `IngestService` (one transaction per file) -> `IngestDao` (`ingest_log`).
- `syllabus/`: `SyllabusDao`, `TopicTagger` (keyword lists), topics seed `seed/syllabus_topics.csv`.
- `service/IdeaService`: new strength formula, proof, CRUD-only flag.
- `web/`: `PeekServlet` (`/peek` JSON), home map data, evaluation page, admin ingest log.
- Schema additions (new tables only, no ALTER): `syllabus_topics`, `project_topics`, `problem_topics`, `suggestion_proof`, `ingest_log`.

## Risks
- Changing `Scorer` signature breaks existing tests: update them in the same task.
- Half-written files in `DB/`: parse only after size is stable across two scans.
- Existing demo DB lacks new tables: `schema.sql` is idempotent (`IF NOT EXISTS`) and runs on every start.

## Tasks (each: implement, run tests or check, then next)
1. Baseline: confirm JDK, Maven, MySQL; `mvn test` count before changes.
2. `TermStats` + `Overlap` with tests (rare terms weigh more; small idea inside big project scores high).
3. Upgrade scorers + `SimilarityEngine` + `Coverage` + `Story`; tests; old-vs-new agreement test on labelled pairs plus new pairs.
4. `MatchIndex` + cached `CorpusIndex`; wire into `CheckService`, `IdeaService`, `RadarService`; tests for index equals brute force.
5. CSV ingest reader + `HeaderMap`; tests with generated files.
6. `ingest_log` schema, `IngestDao`, `IngestService`, `DbFolderWatcher`, `App` wiring; integration test (drop file, wait, project appears).
7. Syllabus schema + topics seed + `TopicTagger`; tests.
8. Suggester rework (new formula, proof, CRUD-only); `problems.csv` topics column; tests.
9. Servlets: `/peek`, map data on home, evaluation stage data, admin ingest log.
10. UI: palette and type decision after viewing current screens; home map and live preview, evaluation page, result story, ideas, admin; responsive, light/dark.
11. Jev: comment out engine calls; app runs on rules only.
12. Verify: full `mvn test`, browser journey, phone width, slop.md re-check.
13. Docs: README, report, slides.
