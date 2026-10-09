-- =====================================================================
-- ORIGIN  -  Project Gap and Similarity Finder
-- MySQL 8 schema: tables (3NF), constraints, views, triggers, a stored
-- function and stored procedures.  Runs in MySQL CLI / Workbench, and is
-- also executed by np.edu.origin.db.SchemaRunner on first start.
-- =====================================================================

-- ---------- Reference data ------------------------------------------------

CREATE TABLE IF NOT EXISTS semesters (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL UNIQUE,          -- e.g. 2026-FALL
    label       VARCHAR(60)  NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT FALSE,
    archived_at DATETIME     NULL
);

CREATE TABLE IF NOT EXISTS domains (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(60)  NOT NULL UNIQUE,
    short_name  VARCHAR(20)  NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS project_types (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(40)  NOT NULL UNIQUE,          -- Web App, Mobile App ...
    effort      TINYINT      NOT NULL CHECK (effort BETWEEN 1 AND 5)  -- build effort for a 3-person team
);

CREATE TABLE IF NOT EXISTS tags (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(40) NOT NULL UNIQUE,
    corpus_uses  INT NOT NULL DEFAULT 0,               -- maintained by triggers
    check_uses   INT NOT NULL DEFAULT 0                -- maintained by triggers
);

CREATE TABLE IF NOT EXISTS tag_synonyms (
    alias   VARCHAR(40) PRIMARY KEY,                   -- "book" -> library
    tag_id  INT NOT NULL,
    CONSTRAINT fk_syn_tag FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
);

-- ---------- People ----------------------------------------------------------

CREATE TABLE IF NOT EXISTS users (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(120) NOT NULL UNIQUE,
    password_hash CHAR(64)     NOT NULL,               -- PBKDF2-HMAC-SHA256, hex
    salt          CHAR(32)     NOT NULL,               -- 16 random bytes, hex
    role          ENUM('STUDENT','SUPERVISOR') NOT NULL DEFAULT 'STUDENT',
    group_code    VARCHAR(10)  NULL,                   -- project group, students only
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------- Historical corpus (grows every semester) -------------------------

CREATE TABLE IF NOT EXISTS corpus_projects (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(160) NOT NULL,
    abstract    TEXT         NOT NULL,
    year        SMALLINT     NOT NULL,
    domain_id   INT          NOT NULL,
    type_id     INT          NOT NULL,
    tech        VARCHAR(200) NULL,                      -- tech stack from the file, where syllabus topics show
    source      ENUM('SEED','ARCHIVED') NOT NULL DEFAULT 'SEED',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cp_domain FOREIGN KEY (domain_id) REFERENCES domains(id),
    CONSTRAINT fk_cp_type   FOREIGN KEY (type_id)   REFERENCES project_types(id),
    FULLTEXT KEY ft_cp (title, abstract)
);

CREATE TABLE IF NOT EXISTS corpus_project_tags (
    project_id INT NOT NULL,
    tag_id     INT NOT NULL,
    PRIMARY KEY (project_id, tag_id),
    CONSTRAINT fk_cpt_project FOREIGN KEY (project_id) REFERENCES corpus_projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_cpt_tag     FOREIGN KEY (tag_id)     REFERENCES tags(id)
);

-- ---------- Idea checks -------------------------------------------------------

CREATE TABLE IF NOT EXISTS idea_checks (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    user_id       INT          NOT NULL,
    semester_id   INT          NOT NULL,
    title         VARCHAR(160) NOT NULL,
    abstract      TEXT         NOT NULL,
    problem       VARCHAR(400) NULL,                   -- "who suffers and how"; feeds the problem bank
    domain_id     INT          NOT NULL,
    type_id       INT          NOT NULL,
    target_semester TINYINT    NOT NULL DEFAULT 4,        -- program semester 1 to 8 the student builds this for; context only
    fingerprint   CHAR(64)     NOT NULL,               -- hash of canonical tags + keywords
    rule_score    DECIMAL(5,4) NOT NULL DEFAULT 0,     -- best explainable score
    final_score   DECIMAL(5,4) NOT NULL DEFAULT 0,     -- after concept blending
    verdict       ENUM('ORIGINAL','SIMILAR','DUPLICATE','LOW_CONFIDENCE') NOT NULL,
    claim_flag    BOOLEAN      NOT NULL DEFAULT FALSE, -- buzzwords not backed by the abstract
    engine_status ENUM('PENDING','DONE','FAILED','OFF') NOT NULL DEFAULT 'PENDING',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ic_user     FOREIGN KEY (user_id)     REFERENCES users(id),
    CONSTRAINT fk_ic_semester FOREIGN KEY (semester_id) REFERENCES semesters(id),
    CONSTRAINT fk_ic_domain   FOREIGN KEY (domain_id)   REFERENCES domains(id),
    CONSTRAINT fk_ic_type     FOREIGN KEY (type_id)     REFERENCES project_types(id),
    INDEX ix_ic_fp (fingerprint)
);

CREATE TABLE IF NOT EXISTS idea_check_tags (
    check_id INT NOT NULL,
    tag_id   INT NOT NULL,
    PRIMARY KEY (check_id, tag_id),
    CONSTRAINT fk_ict_check FOREIGN KEY (check_id) REFERENCES idea_checks(id) ON DELETE CASCADE,
    CONSTRAINT fk_ict_tag   FOREIGN KEY (tag_id)   REFERENCES tags(id)
);

-- ---------- Class Radar: one locked idea per group per semester -----------------

CREATE TABLE IF NOT EXISTS proposals (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    group_code   VARCHAR(10) NOT NULL,
    semester_id  INT         NOT NULL,
    check_id     INT         NOT NULL UNIQUE,
    status       ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    locked_by    INT         NOT NULL,
    locked_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by  INT         NULL,
    reviewed_at  DATETIME    NULL,
    review_note  VARCHAR(255) NULL,
    CONSTRAINT uq_group_semester UNIQUE (group_code, semester_id),
    CONSTRAINT fk_pr_semester FOREIGN KEY (semester_id) REFERENCES semesters(id),
    CONSTRAINT fk_pr_check    FOREIGN KEY (check_id)    REFERENCES idea_checks(id),
    CONSTRAINT fk_pr_locker   FOREIGN KEY (locked_by)   REFERENCES users(id),
    CONSTRAINT fk_pr_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id)
);

-- Scored by the engine against both pools. Exactly one of the two targets is set.
CREATE TABLE IF NOT EXISTS check_matches (
    id                INT AUTO_INCREMENT PRIMARY KEY,
    check_id          INT          NOT NULL,
    pool              ENUM('HISTORY','CLASS') NOT NULL,
    corpus_project_id INT          NULL,
    proposal_id       INT          NULL,
    rank_no           TINYINT      NOT NULL,
    tag_score         DECIMAL(5,4) NOT NULL,
    keyword_score     DECIMAL(5,4) NOT NULL,
    type_score        DECIMAL(5,4) NOT NULL,
    rule_score        DECIMAL(5,4) NOT NULL,
    concept_score     DECIMAL(5,4) NULL,              -- filled in the background
    final_score       DECIMAL(5,4) NOT NULL,
    CONSTRAINT fk_cm_check    FOREIGN KEY (check_id)          REFERENCES idea_checks(id) ON DELETE CASCADE,
    CONSTRAINT fk_cm_corpus   FOREIGN KEY (corpus_project_id) REFERENCES corpus_projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_cm_proposal FOREIGN KEY (proposal_id)       REFERENCES proposals(id) ON DELETE CASCADE,
    CONSTRAINT ck_cm_target CHECK ((corpus_project_id IS NULL) <> (proposal_id IS NULL))
);

CREATE TABLE IF NOT EXISTS radar_collisions (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    proposal_a   INT          NOT NULL,               -- the older proposal
    proposal_b   INT          NOT NULL,               -- the newer proposal that collided with it
    score        DECIMAL(5,4) NOT NULL,
    detected_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_collision UNIQUE (proposal_a, proposal_b),
    CONSTRAINT fk_rc_a FOREIGN KEY (proposal_a) REFERENCES proposals(id) ON DELETE CASCADE,
    CONSTRAINT fk_rc_b FOREIGN KEY (proposal_b) REFERENCES proposals(id) ON DELETE CASCADE
);

-- ---------- Ideas: problem bank + generated suggestions ---------------------------

CREATE TABLE IF NOT EXISTS problem_bank (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    domain_id       INT          NOT NULL,
    statement       VARCHAR(400) NOT NULL,            -- the real problem
    affected        VARCHAR(120) NOT NULL,            -- who suffers
    solution_phrase VARCHAR(120) NOT NULL,            -- what could be built
    primary_type_id INT          NOT NULL,
    alt_type_id     INT          NULL,
    source          ENUM('SEED','STUDENT') NOT NULL DEFAULT 'SEED',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pb_domain FOREIGN KEY (domain_id)       REFERENCES domains(id),
    CONSTRAINT fk_pb_type1  FOREIGN KEY (primary_type_id) REFERENCES project_types(id),
    CONSTRAINT fk_pb_type2  FOREIGN KEY (alt_type_id)     REFERENCES project_types(id),
    CONSTRAINT uq_pb_statement UNIQUE (statement(190))
);

CREATE TABLE IF NOT EXISTS problem_tags (
    problem_id INT NOT NULL,
    tag_id     INT NOT NULL,
    PRIMARY KEY (problem_id, tag_id),
    CONSTRAINT fk_pt_problem FOREIGN KEY (problem_id) REFERENCES problem_bank(id) ON DELETE CASCADE,
    CONSTRAINT fk_pt_tag     FOREIGN KEY (tag_id)     REFERENCES tags(id)
);

CREATE TABLE IF NOT EXISTS idea_suggestions (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    problem_id     INT          NOT NULL,
    type_id        INT          NOT NULL,
    title          VARCHAR(160) NOT NULL,
    gap_count      INT          NOT NULL,             -- corpus projects already in this domain x type cell
    nearest_score  DECIMAL(5,4) NOT NULL,             -- closest existing project / proposal
    nearest_title  VARCHAR(160) NULL,
    novelty        DECIMAL(5,4) NOT NULL,             -- 0..1
    feasibility    DECIMAL(5,4) NOT NULL,             -- 0..1
    impact         DECIMAL(5,4) NOT NULL,             -- 0..1
    difficulty     TINYINT      NOT NULL,             -- 1 easy .. 4 hard
    strength       DECIMAL(5,4) NOT NULL,             -- weighted total used for ranking
    status         ENUM('OPEN','TAKEN','RETIRED') NOT NULL DEFAULT 'OPEN',
    scored_by      ENUM('RULES','ENGINE') NOT NULL DEFAULT 'RULES',
    taken_by_group VARCHAR(10)  NULL,
    refreshed_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_suggestion UNIQUE (problem_id, type_id),
    CONSTRAINT fk_is_problem FOREIGN KEY (problem_id) REFERENCES problem_bank(id) ON DELETE CASCADE,
    CONSTRAINT fk_is_type    FOREIGN KEY (type_id)    REFERENCES project_types(id)
);

-- ---------- Memory of supervisor corrections -----------------------------------

CREATE TABLE IF NOT EXISTS match_overrides (
    id                INT AUTO_INCREMENT PRIMARY KEY,
    fingerprint       CHAR(64) NOT NULL,
    corpus_project_id INT      NOT NULL,
    same_project      BOOLEAN  NOT NULL,
    supervisor_id     INT      NOT NULL,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_override UNIQUE (fingerprint, corpus_project_id),
    CONSTRAINT fk_mo_project    FOREIGN KEY (corpus_project_id) REFERENCES corpus_projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_mo_supervisor FOREIGN KEY (supervisor_id)     REFERENCES users(id)
);

-- Cache of decision-engine answers so the same question is never asked twice.
CREATE TABLE IF NOT EXISTS engine_cache (
    cache_key   CHAR(64)     PRIMARY KEY,             -- SHA-256 of the request body
    answer_json TEXT         NOT NULL,
    model       VARCHAR(40)  NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_log (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_id   INT          NULL,
    action     VARCHAR(40)  NOT NULL,
    entity     VARCHAR(40)  NOT NULL,
    entity_id  INT          NULL,
    detail     VARCHAR(255) NULL,
    at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------- DB folder auto-ingest ---------------------------------------------------

-- Projects read from files dropped into the DB folder are marked IMPORTED.
ALTER TABLE corpus_projects MODIFY COLUMN source ENUM('SEED','ARCHIVED','IMPORTED') NOT NULL DEFAULT 'SEED';

-- One row per file the folder watcher has dealt with. size + modified identify the file version,
-- so a changed file is read again and an unchanged one never is.
CREATE TABLE IF NOT EXISTS ingest_log (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    file_name      VARCHAR(255) NOT NULL,
    file_size      BIGINT       NOT NULL,
    file_modified  BIGINT       NOT NULL,              -- epoch milliseconds
    status         ENUM('DONE','FAILED','UNSUPPORTED') NOT NULL,
    rows_added     INT          NOT NULL DEFAULT 0,
    rows_skipped   INT          NOT NULL DEFAULT 0,
    note           VARCHAR(500) NULL,
    processed_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX ix_ingest_file (file_name, file_size, file_modified)
);

-- ---------- Syllabus topics: what each project and each suggestion uses from the BIT syllabus ----------

CREATE TABLE IF NOT EXISTS syllabus_topics (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    course    ENUM('JAVA','DBMS','OS','COA','STATS') NOT NULL,
    unit_no   TINYINT      NOT NULL,
    name      VARCHAR(80)  NOT NULL UNIQUE,
    crud      BOOLEAN      NOT NULL DEFAULT FALSE,     -- everyday CRUD plumbing (JDBC, Servlet/JSP, ER design)
    keywords  VARCHAR(300) NOT NULL                    -- comma-separated words that show a project uses it
);

CREATE TABLE IF NOT EXISTS corpus_project_topics (
    project_id INT NOT NULL,
    topic_id   INT NOT NULL,
    PRIMARY KEY (project_id, topic_id),
    CONSTRAINT fk_cpto_project FOREIGN KEY (project_id) REFERENCES corpus_projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_cpto_topic   FOREIGN KEY (topic_id)   REFERENCES syllabus_topics(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS problem_topics (
    problem_id INT NOT NULL,
    topic_id   INT NOT NULL,
    PRIMARY KEY (problem_id, topic_id),
    CONSTRAINT fk_ptop_problem FOREIGN KEY (problem_id) REFERENCES problem_bank(id) ON DELETE CASCADE,
    CONSTRAINT fk_ptop_topic   FOREIGN KEY (topic_id)   REFERENCES syllabus_topics(id) ON DELETE CASCADE
);

-- One sentence on how the syllabus topics make this problem special.
CREATE TABLE IF NOT EXISTS problem_twist (
    problem_id INT PRIMARY KEY,
    twist      VARCHAR(400) NOT NULL,
    CONSTRAINT fk_twist_problem FOREIGN KEY (problem_id) REFERENCES problem_bank(id) ON DELETE CASCADE
);

-- The numbers behind a suggestion, so the page can show its proof.
CREATE TABLE IF NOT EXISTS suggestion_proof (
    suggestion_id  INT PRIMARY KEY,
    pair_label     VARCHAR(160) NULL,                  -- the busiest topic pair, for example "Triggers + Backup and recovery"
    pair_uses      INT          NOT NULL DEFAULT 0,    -- past projects that used that pair together
    total_projects INT          NOT NULL DEFAULT 0,
    units          INT          NOT NULL DEFAULT 0,    -- distinct syllabus units the suggestion touches
    courses        INT          NOT NULL DEFAULT 0,
    crud_only      BOOLEAN      NOT NULL DEFAULT FALSE,
    topic_list     VARCHAR(400) NOT NULL,
    CONSTRAINT fk_proof_suggestion FOREIGN KEY (suggestion_id) REFERENCES idea_suggestions(id) ON DELETE CASCADE
);

-- =====================================================================
-- Stored function
-- =====================================================================
DROP FUNCTION IF EXISTS fn_band;
DELIMITER $$
CREATE FUNCTION fn_band(p_score DECIMAL(5,4)) RETURNS VARCHAR(20) DETERMINISTIC
BEGIN
    IF p_score >= 0.6000 THEN RETURN 'DUPLICATE';
    ELSEIF p_score >= 0.3500 THEN RETURN 'SIMILAR';
    END IF;
    RETURN 'ORIGINAL';
END$$
DELIMITER ;

-- =====================================================================
-- Triggers: the database keeps its own statistics and audit trail
-- =====================================================================
DROP TRIGGER IF EXISTS trg_cpt_count;
DELIMITER $$
CREATE TRIGGER trg_cpt_count AFTER INSERT ON corpus_project_tags FOR EACH ROW
BEGIN
    UPDATE tags SET corpus_uses = corpus_uses + 1 WHERE id = NEW.tag_id;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_ict_count;
DELIMITER $$
CREATE TRIGGER trg_ict_count AFTER INSERT ON idea_check_tags FOR EACH ROW
BEGIN
    UPDATE tags SET check_uses = check_uses + 1 WHERE id = NEW.tag_id;
    -- the tags follow a student-reported problem into the problem bank
    INSERT IGNORE INTO problem_tags (problem_id, tag_id)
    SELECT pb.id, NEW.tag_id
    FROM idea_checks ic
    JOIN problem_bank pb ON pb.source = 'STUDENT' AND pb.statement = TRIM(ic.problem)
    WHERE ic.id = NEW.check_id;
END$$
DELIMITER ;

-- A student's own problem statement becomes part of the problem bank automatically.
DROP TRIGGER IF EXISTS trg_check_problem;
DELIMITER $$
CREATE TRIGGER trg_check_problem AFTER INSERT ON idea_checks FOR EACH ROW
BEGIN
    IF NEW.problem IS NOT NULL AND CHAR_LENGTH(TRIM(NEW.problem)) >= 25 THEN
        INSERT IGNORE INTO problem_bank (domain_id, statement, affected, solution_phrase, primary_type_id, source)
        VALUES (NEW.domain_id, TRIM(NEW.problem), 'Reported by a student', NEW.title, NEW.type_id, 'STUDENT');
    END IF;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_proposal_locked;
DELIMITER $$
CREATE TRIGGER trg_proposal_locked AFTER INSERT ON proposals FOR EACH ROW
BEGIN
    INSERT INTO audit_log (actor_id, action, entity, entity_id, detail)
    VALUES (NEW.locked_by, 'LOCK', 'proposal', NEW.id, CONCAT('Group ', NEW.group_code, ' locked check ', NEW.check_id));
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_proposal_reviewed;
DELIMITER $$
CREATE TRIGGER trg_proposal_reviewed AFTER UPDATE ON proposals FOR EACH ROW
BEGIN
    IF NEW.status <> OLD.status THEN
        INSERT INTO audit_log (actor_id, action, entity, entity_id, detail)
        VALUES (NEW.reviewed_by, NEW.status, 'proposal', NEW.id,
                CONCAT('Group ', NEW.group_code, ': ', OLD.status, ' -> ', NEW.status));
    END IF;
END$$
DELIMITER ;

-- Every Class Radar collision is written to the audit trail by the database itself.
DROP TRIGGER IF EXISTS trg_collision_audit;
DELIMITER $$
CREATE TRIGGER trg_collision_audit AFTER INSERT ON radar_collisions FOR EACH ROW
BEGIN
    INSERT INTO audit_log (action, entity, entity_id, detail)
    VALUES ('COLLISION', 'proposal', NEW.proposal_b,
            CONCAT('Collides with proposal ', NEW.proposal_a, ' at ', ROUND(NEW.score * 100), '%'));
END$$
DELIMITER ;

-- =====================================================================
-- Views
-- =====================================================================

-- The gap map: every domain x type cell, including empty ones (cross join).
CREATE OR REPLACE VIEW v_gap_matrix AS
SELECT d.id AS domain_id, d.name AS domain_name, d.short_name,
       t.id AS type_id,   t.name AS type_name,
       (SELECT COUNT(*) FROM corpus_projects cp
         WHERE cp.domain_id = d.id AND cp.type_id = t.id) AS project_count,
       (SELECT COUNT(*) FROM proposals p JOIN idea_checks ic ON ic.id = p.check_id
          JOIN semesters s ON s.id = p.semester_id AND s.is_active
         WHERE ic.domain_id = d.id AND ic.type_id = t.id) AS live_count,
       (SELECT COUNT(*) FROM idea_suggestions sg JOIN problem_bank pb ON pb.id = sg.problem_id
         WHERE pb.domain_id = d.id AND sg.type_id = t.id AND sg.status = 'OPEN') AS open_ideas
FROM domains d CROSS JOIN project_types t;

-- Saturation per domain across all years.
CREATE OR REPLACE VIEW v_domain_saturation AS
SELECT d.id AS domain_id, d.name AS domain_name,
       COUNT(cp.id) AS project_count,
       MAX(cp.year) AS last_year,
       ROUND(COUNT(cp.id) / NULLIF((SELECT COUNT(*) FROM corpus_projects), 0) * 100, 1) AS share_pct
FROM domains d LEFT JOIN corpus_projects cp ON cp.domain_id = d.id
GROUP BY d.id, d.name;

-- This semester's locked ideas with their collision count.
CREATE OR REPLACE VIEW v_class_radar AS
SELECT p.id AS proposal_id, p.group_code, p.status, p.locked_at,
       ic.id AS check_id, ic.title, ic.abstract, ic.verdict, ic.final_score,
       d.name AS domain_name, t.name AS type_name, u.full_name AS locked_by_name,
       (SELECT COUNT(*) FROM radar_collisions rc
         WHERE rc.proposal_a = p.id OR rc.proposal_b = p.id) AS collisions
FROM proposals p
JOIN semesters s      ON s.id = p.semester_id AND s.is_active
JOIN idea_checks ic   ON ic.id = p.check_id
JOIN domains d        ON d.id = ic.domain_id
JOIN project_types t  ON t.id = ic.type_id
JOIN users u          ON u.id = p.locked_by;

-- Check history with names resolved (used for the CSV export).
CREATE OR REPLACE VIEW v_check_history AS
SELECT ic.id, ic.user_id, ic.created_at, ic.title, d.name AS domain_name, t.name AS type_name,
       ic.verdict, ROUND(ic.final_score * 100) AS score_pct, ic.claim_flag,
       (SELECT cp.title FROM check_matches cm JOIN corpus_projects cp ON cp.id = cm.corpus_project_id
         WHERE cm.check_id = ic.id AND cm.pool = 'HISTORY' ORDER BY cm.final_score DESC LIMIT 1) AS closest_project
FROM idea_checks ic
JOIN domains d       ON d.id = ic.domain_id
JOIN project_types t ON t.id = ic.type_id;

-- =====================================================================
-- Stored procedures
-- =====================================================================

-- End of semester: approved proposals join the corpus, the semester closes,
-- a new one opens.  All-or-nothing (transaction with rollback on error).
DROP PROCEDURE IF EXISTS sp_archive_semester;
DELIMITER $$
CREATE PROCEDURE sp_archive_semester(IN p_new_code VARCHAR(20), IN p_new_label VARCHAR(60),
                                     IN p_actor INT, OUT p_moved INT)
BEGIN
    DECLARE v_sem INT;
    DECLARE v_year SMALLINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
    SELECT id INTO v_sem FROM semesters WHERE is_active LIMIT 1 FOR UPDATE;
    SET v_year = YEAR(CURRENT_DATE);

    -- 1. copy approved ideas into the corpus
    INSERT INTO corpus_projects (title, abstract, year, domain_id, type_id, source)
    SELECT ic.title, ic.abstract, v_year, ic.domain_id, ic.type_id, 'ARCHIVED'
    FROM proposals p JOIN idea_checks ic ON ic.id = p.check_id
    WHERE p.semester_id = v_sem AND p.status = 'APPROVED';
    SET p_moved = ROW_COUNT();

    -- 2. copy their tags (matched back by title + year, source ARCHIVED)
    INSERT IGNORE INTO corpus_project_tags (project_id, tag_id)
    SELECT cp.id, ict.tag_id
    FROM proposals p
    JOIN idea_checks ic      ON ic.id = p.check_id
    JOIN idea_check_tags ict ON ict.check_id = ic.id
    JOIN corpus_projects cp  ON cp.title = ic.title AND cp.year = v_year AND cp.source = 'ARCHIVED'
    WHERE p.semester_id = v_sem AND p.status = 'APPROVED';

    -- 3. close this semester, open the next
    UPDATE semesters SET is_active = FALSE, archived_at = NOW() WHERE id = v_sem;
    INSERT INTO semesters (code, label, is_active) VALUES (p_new_code, p_new_label, TRUE);

    -- 4. ideas are re-scored against the bigger corpus by the maintenance job
    UPDATE idea_suggestions SET status = 'OPEN', taken_by_group = NULL WHERE status = 'TAKEN';

    INSERT INTO audit_log (actor_id, action, entity, entity_id, detail)
    VALUES (p_actor, 'ARCHIVE', 'semester', v_sem, CONCAT(p_moved, ' approved projects moved to corpus'));
    COMMIT;
END$$
DELIMITER ;

-- Supervisor report: one row per group with verdict, status and collisions.
DROP PROCEDURE IF EXISTS sp_semester_report;
DELIMITER $$
CREATE PROCEDURE sp_semester_report()
BEGIN
    SELECT r.group_code, r.title, r.domain_name, r.type_name, r.status,
           ROUND(r.final_score * 100) AS similarity_pct, fn_band(r.final_score) AS band, r.collisions
    FROM v_class_radar r
    ORDER BY r.group_code;
END$$
DELIMITER ;
