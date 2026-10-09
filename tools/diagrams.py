"""Draws the report diagrams as SVG and renders each one to a PNG through Chromium.

Sizes are chosen for print: portrait diagrams are 1000 px wide and fill a 15.3 cm column, landscape ones are
about 1500 px wide and fill 24 cm, so 17 to 20 px text prints at roughly 7.5 to 8.5 pt.

Usage: py tools/diagrams.py [out_dir]
"""
import sys
from pathlib import Path
from playwright.sync_api import sync_playwright

OUT = Path(sys.argv[1] if len(sys.argv) > 1 else "docs/diagrams")
OUT.mkdir(parents=True, exist_ok=True)

INK = "#12352a"
SOFT = "#e8efe9"
PALE = "#f6f8f4"
WARM = "#fbf7ee"
OCHRE = "#8c5f1c"
NOTE = "#365547"
FONT = "Segoe UI, Arial, sans-serif"


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def text(x, y, s, size=18, weight=400, anchor="middle", fill=INK):
    lines = s.split("\n")
    first = y - (len(lines) - 1) * size * 0.62
    out = []
    for i, ln in enumerate(lines):
        out.append(f'<text x="{x}" y="{first + i * size * 1.24:.1f}" font-family="{FONT}" font-size="{size}" '
                   f'font-weight="{weight}" text-anchor="{anchor}" dominant-baseline="central" fill="{fill}">{esc(ln)}</text>')
    return "".join(out)


def box(x, y, w, h, label_text="", fill="#ffffff", stroke=INK, rx=6, size=18, weight=500, dash=None, sw=1.8):
    d = f' stroke-dasharray="{dash}"' if dash else ""
    s = f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}"{d}/>'
    if label_text:
        s += text(x + w / 2, y + h / 2, label_text, size, weight)
    return s


def line(x1, y1, x2, y2, head=True, dash=None, sw=1.8, both=False):
    d = f' stroke-dasharray="{dash}"' if dash else ""
    m = ' marker-end="url(#arr)"' if head else ""
    m += ' marker-start="url(#arrs)"' if both else ""
    return f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{INK}" stroke-width="{sw}"{d}{m}/>'


def path(d, head=True, dash=None, sw=1.8):
    ds = f' stroke-dasharray="{dash}"' if dash else ""
    m = ' marker-end="url(#arr)"' if head else ""
    return f'<path d="{d}" fill="none" stroke="{INK}" stroke-width="{sw}"{ds}{m}/>'


def label(x, y, s, size=16, anchor="middle", fill=NOTE, weight=400):
    """A flow label with a white halo, so it stays readable where it sits near a line."""
    lines = s.split("\n")
    first = y - (len(lines) - 1) * size * 0.62
    out = []
    for i, ln in enumerate(lines):
        yy = first + i * size * 1.24
        out.append(f'<text x="{x}" y="{yy:.1f}" font-family="{FONT}" font-size="{size}" font-weight="{weight}" '
                   f'text-anchor="{anchor}" dominant-baseline="central" fill="{fill}" stroke="#ffffff" stroke-width="5" '
                   f'paint-order="stroke">{esc(ln)}</text>')
    return "".join(out)


def svg(w, h, body):
    defs = (f'<defs><marker id="arr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto">'
            f'<path d="M0,0 L10,5 L0,10 z" fill="{INK}"/></marker>'
            f'<marker id="arrs" viewBox="0 0 10 10" refX="1" refY="5" markerWidth="8" markerHeight="8" orient="auto">'
            f'<path d="M10,0 L0,5 L10,10 z" fill="{INK}"/></marker></defs>')
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">'
            f'<rect width="{w}" height="{h}" fill="#ffffff"/>{defs}{body}</svg>')


# ---------------------------------------------------------------- 1. SDLC (portrait)
def sdlc():
    b = [text(500, 34, "Iterative and incremental development", 26, 700)]
    incs = [("Increment 1", "Schema, seed data, login"), ("Increment 2", "Rule engine, check flow"),
            ("Increment 3", "Class Radar, gap map"), ("Increment 4", "Ideas, DB-folder ingest"),
            ("Increment 5", "Syllabus map, tests, report")]
    phases = ["Plan", "Design", "Build", "Test"]
    for i, (name, goal) in enumerate(incs):
        y0 = 80 + i * 132
        b.append(box(30, y0, 300, 104, fill=SOFT if i % 2 == 0 else PALE, rx=8))
        b.append(text(180, y0 + 34, name, 20, 700))
        b.append(text(180, y0 + 72, goal, 17, 400, fill=NOTE))
        b.append(line(330, y0 + 52, 366, y0 + 52))
        for j, ph in enumerate(phases):
            x = 370 + j * 150
            b.append(box(x, y0 + 26, 118, 52, ph, fill="#ffffff", size=18))
            if j < 3:
                b.append(line(x + 118, y0 + 52, x + 148, y0 + 52))
        if i < 4:
            b.append(line(180, y0 + 104, 180, y0 + 130))
            b.append(label(195, y0 + 117, "working release", 16, anchor="start", fill=OCHRE, weight=600))
    last = 80 + 4 * 132 + 52
    b.append(path(f"M 938 {last} L 972 {last} L 972 132 L 940 132", dash="7 6"))
    b.append(text(500, 760, "Dashed line: feedback from each increment's review shapes the next one.", 17, 400, fill=NOTE))
    return svg(1000, 790, "".join(b))


# ---------------------------------------------------------------- 2. Architecture (portrait)
def architecture():
    b = [text(500, 32, "Origin system architecture", 26, 700)]
    b.append(box(30, 60, 940, 90, fill=SOFT, rx=8))
    b.append(text(500, 88, "Browser", 20, 700))
    b.append(text(500, 124, "JSP pages as HTML  ·  origin.css  ·  map.js, evaluating.js", 17))
    b.append(line(500, 150, 500, 188, both=True))
    b.append(label(530, 169, "HTTP", 16, anchor="start"))
    b.append(box(30, 190, 940, 240, fill="#ffffff", rx=8))
    b.append(text(50, 216, "Apache Tomcat 10.1", 19, 700, anchor="start"))
    b.append(box(50, 240, 280, 170, fill=SOFT))
    b.append(text(190, 272, "SecurityFilter", 18, 700))
    b.append(text(190, 345, "session and role\nCSRF token\nsecurity headers", 17))
    b.append(box(360, 240, 300, 170, fill=SOFT))
    b.append(text(510, 272, "Servlets", 18, 700))
    b.append(text(510, 345, "Auth · Check · Explore\nAdmin · Peek (JSON)\nAppListener", 17))
    b.append(box(690, 240, 260, 170, fill=SOFT))
    b.append(text(820, 272, "JSP views", 18, 700))
    b.append(text(820, 345, "12 pages\n3 fragments\nJSTL tags", 17))
    b.append(line(330, 325, 358, 325))
    b.append(line(660, 325, 688, 325))
    b.append(line(335, 430, 335, 468))
    b.append(box(30, 470, 610, 250, fill="#ffffff", rx=8))
    b.append(text(50, 496, "Service layer", 19, 700, anchor="start"))
    for i, s in enumerate(["CheckService", "RadarService", "IdeaService"]):
        b.append(box(50 + i * 195, 515, 180, 46, s, fill=SOFT, size=16))
    for i, s in enumerate(["IngestService", "SyllabusService", "GapService"]):
        b.append(box(50 + i * 195, 574, 180, 46, s, fill=SOFT, size=16))
    b.append(box(50, 636, 570, 68, "Engine: SimilarityEngine, three Scorers, MatchIndex,\nTermStats, Vocabulary, ClaimDetector", fill="#f4ecdc", size=16))
    b.append(box(670, 470, 300, 250, fill=WARM, rx=8, dash="7 5"))
    b.append(text(820, 500, "Background threads", 19, 700))
    b.append(text(820, 566, "DB folder watcher\nevery 5 seconds\nmaintenance timer\nevery 10 minutes", 17))
    b.append(text(820, 672, "read files,\nrefresh ideas", 16, 600, fill=OCHRE))
    b.append(line(640, 595, 668, 595, both=True))
    b.append(line(335, 720, 335, 758))
    b.append(box(30, 760, 610, 100, fill=SOFT, rx=8))
    b.append(text(335, 790, "DAO layer (JDBC)", 19, 700))
    b.append(text(335, 830, "PreparedStatement, transactions, CallableStatement", 16))
    b.append(line(335, 860, 335, 898))
    b.append(label(350, 879, "JDBC", 16, anchor="start"))
    b.append(box(30, 900, 610, 130, fill="#ffffff", rx=8, sw=2.4))
    b.append(text(335, 934, "MySQL 8", 20, 700))
    b.append(text(335, 988, "25 tables (3NF) · 4 views\n6 triggers · fn_band · 2 procedures", 17))
    b.append(box(670, 860, 300, 170, fill="#ffffff", rx=8, dash="7 5"))
    b.append(text(820, 900, "DB folder\n(past-project files)", 18, 700))
    b.append(text(820, 978, "CSV · TXT · XLSX · DOCX\nread with java.io and\njava.util.zip", 16))
    b.append(line(820, 858, 820, 722))
    b.append(label(835, 790, "files", 16, anchor="start"))
    return svg(1000, 1060, "".join(b))


# ---------------------------------------------------------------- 3. DFD level 0 (portrait)
def dfd0():
    b = [text(500, 34, "Data flow diagram, level 0 (context)", 26, 700)]
    b.append(f'<circle cx="500" cy="300" r="125" fill="{SOFT}" stroke="{INK}" stroke-width="2.2"/>')
    b.append(text(500, 250, "0", 20, 700))
    b.append(text(500, 288, "Origin", 21, 700))
    b.append(text(500, 334, "project gap and\nsimilarity finder", 17))
    b.append(box(20, 260, 170, 80, "Student", size=20, weight=700, rx=2))
    b.append(box(810, 260, 170, 80, "Supervisor", size=20, weight=700, rx=2))
    b.append(box(390, 540, 220, 80, "DB folder", size=19, weight=700, rx=2))
    b.append(line(190, 276, 379, 264))
    b.append(label(285, 226, "idea, tags,\nlock request"))
    b.append(line(380, 336, 192, 324))
    b.append(label(285, 378, "verdict, matches,\ngap map, ideas"))
    b.append(line(810, 276, 621, 264))
    b.append(label(715, 226, "review, corrections,\nclose semester"))
    b.append(line(620, 336, 808, 324))
    b.append(label(715, 378, "clashes, ingest log,\nreport"))
    b.append(line(500, 538, 500, 426))
    b.append(label(515, 482, "past-project files", anchor="start"))
    return svg(1000, 650, "".join(b))


# ---------------------------------------------------------------- 4. DFD level 1 (landscape)
def proc(x, y, num, name, w=220, h=100):
    s = box(x, y, w, h, fill=SOFT, rx=16)
    s += f'<line x1="{x}" y1="{y + 30}" x2="{x + w}" y2="{y + 30}" stroke="{INK}" stroke-width="1.4"/>'
    s += text(x + w / 2, y + 15, num, 17, 700)
    s += text(x + w / 2, y + 65, name, 18, 600)
    return s


def store(x, y, code, name, w=280):
    s = f'<path d="M {x + w} {y} L {x} {y} L {x} {y + 46} L {x + w} {y + 46}" fill="#ffffff" stroke="{INK}" stroke-width="1.8"/>'
    s += f'<line x1="{x + 52}" y1="{y}" x2="{x + 52}" y2="{y + 46}" stroke="{INK}" stroke-width="1.4"/>'
    s += text(x + 26, y + 23, code, 17, 700)
    s += text(x + 64, y + 23, name, 18, 500, anchor="start")
    return s


def dfd1():
    b = [text(750, 32, "Data flow diagram, level 1", 26, 700)]
    b.append(box(20, 440, 160, 80, "Student", size=20, weight=700, rx=2))
    b.append(box(1320, 450, 160, 80, "Supervisor", size=19, weight=700, rx=2))
    b.append(box(1300, 880, 180, 80, "DB folder\n(files)", size=18, weight=700, rx=2))
    b.append(proc(250, 100, "1.0", "Authenticate"))
    b.append(proc(250, 420, "2.0", "Check idea"))
    b.append(proc(250, 740, "3.0", "Lock and\nClass Radar"))
    b.append(proc(970, 160, "4.0", "Build gap map\nand ideas"))
    b.append(proc(970, 440, "5.0", "Review and\narchive"))
    b.append(proc(970, 770, "6.0", "Read the\nDB folder"))
    for code, name, y in [("D1", "Users", 70), ("D2", "Corpus + topics", 200), ("D3", "Problems + ideas", 345),
                          ("D4", "Proposals + clashes", 490), ("D5", "Checks + matches", 635),
                          ("D6", "Audit log", 780), ("D7", "Ingest log", 900)]:
        b.append(store(580, y, code, name))
    # student and the left-hand processes
    b.append(line(180, 450, 248, 162))
    b.append(label(150, 300, "credentials"))
    b.append(line(180, 468, 248, 468))
    b.append(label(214, 452, "idea"))
    b.append(line(250, 500, 182, 500))
    b.append(label(214, 520, "verdict"))
    b.append(line(180, 512, 248, 790))
    b.append(label(160, 660, "lock"))
    b.append(line(470, 140, 578, 96))
    b.append(label(528, 104, "read"))
    b.append(line(470, 440, 578, 228))
    b.append(label(505, 330, "read"))
    b.append(line(470, 470, 578, 508))
    b.append(label(528, 474, "read"))
    b.append(line(470, 500, 578, 652))
    b.append(label(512, 590, "write"))
    b.append(line(470, 760, 578, 522))
    b.append(label(548, 650, "write"))
    b.append(line(470, 810, 578, 803))
    b.append(label(524, 792, "trigger"))
    # right-hand processes use the open side of each store
    b.append(line(862, 222, 968, 200))
    b.append(label(914, 196, "read"))
    b.append(line(968, 236, 864, 362))
    b.append(label(900, 282, "write"))
    b.append(line(862, 506, 968, 256))
    b.append(label(940, 380, "read"))
    b.append(line(968, 452, 864, 236))
    b.append(label(884, 318, "archive"))
    b.append(line(968, 484, 864, 510))
    b.append(label(918, 474, "update"))
    b.append(line(1190, 200, 1320, 460))
    b.append(label(1290, 300, "gap map,\nideas"))
    b.append(line(1320, 474, 1192, 474))
    b.append(label(1256, 440, "approve,\narchive"))
    b.append(line(1190, 512, 1318, 512))
    b.append(label(1255, 530, "report"))
    b.append(line(968, 780, 864, 232))
    b.append(label(906, 600, "add\nprojects"))
    b.append(line(968, 850, 864, 922))
    b.append(label(915, 902, "log"))
    b.append(line(1300, 930, 1192, 845))
    b.append(label(1262, 912, "files"))
    return svg(1500, 980, "".join(b))


# ---------------------------------------------------------------- 5. ER (landscape)
def entity(x, y, name, cols, w=210):
    h = 38 + 27 * len(cols)
    s = box(x, y, w, h, fill="#ffffff", rx=4)
    s += f'<rect x="{x}" y="{y}" width="{w}" height="34" rx="4" fill="{INK}"/>'
    s += text(x + w / 2, y + 17, name, 18, 700, fill="#ffffff")
    for i, (c, k) in enumerate(cols):
        yy = y + 52 + i * 27
        s += text(x + 10, yy, c, 17, 700 if "PK" in k else 400, anchor="start")
        if k:
            s += text(x + w - 10, yy, k, 13, 700, anchor="end", fill=OCHRE if "FK" in k else INK)
    return s, h


def er():
    b = [text(760, 30, "Entity relationship diagram (25 tables, crow's foot notation)", 24, 700)]
    E = {}

    def ent(key, x, y, cols):
        s, h = entity(x, y, key, cols)
        b.append(s)
        E[key] = (x, y, 210, h)

    C = [20, 272, 524, 776, 1028, 1280]
    ent("semesters", C[0], 64, [("id", "PK"), ("code", "UQ"), ("is_active", ""), ("archived_at", "")])
    ent("users", C[0], 280, [("id", "PK"), ("email", "UQ"), ("full_name", ""), ("role", ""), ("group_code", "")])
    ent("audit_log", C[0], 570, [("id", "PK"), ("actor_id", ""), ("action", ""), ("detail", "")])
    ent("idea_checks", C[1], 64, [("id", "PK"), ("user_id", "FK"), ("semester_id", "FK"), ("domain_id", "FK"),
                                  ("type_id", "FK"), ("title", ""), ("fingerprint", ""), ("final_score", ""), ("verdict", "")])
    ent("proposals", C[1], 420, [("id", "PK"), ("group_code", "UQ*"), ("semester_id", "FK"), ("check_id", "FK UQ"),
                                 ("locked_by", "FK"), ("reviewed_by", "FK"), ("status", "")])
    ent("radar_collisions", C[1], 700, [("id", "PK"), ("proposal_a", "FK"), ("proposal_b", "FK"), ("score", "")])
    ent("idea_check_tags", C[2], 64, [("check_id", "PK FK"), ("tag_id", "PK FK")])
    ent("check_matches", C[2], 200, [("id", "PK"), ("check_id", "FK"), ("pool", ""), ("corpus_project_id", "FK"),
                                     ("proposal_id", "FK"), ("final_score", "")])
    ent("match_overrides", C[2], 460, [("id", "PK"), ("fingerprint", "UQ*"), ("corpus_project_id", "FK"),
                                       ("same_project", ""), ("supervisor_id", "FK")])
    ent("engine_cache", C[2], 700, [("cache_key", "PK"), ("answer_json", ""), ("model", "")])
    ent("tags", C[3], 64, [("id", "PK"), ("name", "UQ"), ("corpus_uses", ""), ("check_uses", "")])
    ent("tag_synonyms", C[3], 236, [("alias", "PK"), ("tag_id", "FK")])
    ent("corpus_projects", C[3], 368, [("id", "PK"), ("title", ""), ("year", ""), ("domain_id", "FK"),
                                       ("type_id", "FK"), ("tech", ""), ("source", "")])
    ent("corpus_project_tags", C[3], 610, [("project_id", "PK FK"), ("tag_id", "PK FK")])
    ent("domains", C[4], 64, [("id", "PK"), ("name", "UQ"), ("short_name", "UQ")])
    ent("project_types", C[4], 222, [("id", "PK"), ("name", "UQ"), ("effort", "")])
    ent("problem_bank", C[4], 390, [("id", "PK"), ("domain_id", "FK"), ("primary_type_id", "FK"),
                                    ("alt_type_id", "FK"), ("statement", "UQ"), ("source", "")])
    ent("idea_suggestions", C[5], 64, [("id", "PK"), ("problem_id", "FK"), ("type_id", "FK"), ("title", ""),
                                       ("strength", ""), ("status", "")])
    ent("problem_tags", C[5], 390, [("problem_id", "PK FK"), ("tag_id", "PK FK")])
    # v3: DB-folder ingest and the syllabus layer
    ent("ingest_log", C[0], 745, [("id", "PK"), ("file_name", ""), ("file_size", ""), ("status", ""), ("rows_added", "")])
    ent("corpus_project_topics", C[1], 900, [("project_id", "PK FK"), ("topic_id", "PK FK")])
    ent("syllabus_topics", C[2], 900, [("id", "PK"), ("course", ""), ("unit_no", ""), ("name", "UQ"), ("crud", "")])
    ent("problem_topics", C[3], 900, [("problem_id", "PK FK"), ("topic_id", "PK FK")])
    ent("problem_twist", C[4], 900, [("problem_id", "PK FK"), ("twist", "")])
    ent("suggestion_proof", C[5], 900, [("suggestion_id", "PK FK"), ("pair_uses", ""), ("units", ""), ("crud_only", ""),
                                        ("topic_list", "")])

    def side(k, where, t=0.5):
        x, y, w, h = E[k]
        return {"l": (x, y + h * t), "r": (x + w, y + h * t), "t": (x + w * t, y), "b": (x + w * t, y + h)}[where]

    def one(x, y, where):
        if where in ("l", "r"):
            dx = 10 if where == "r" else -10
            return f'<line x1="{x + dx}" y1="{y - 8}" x2="{x + dx}" y2="{y + 8}" stroke="{INK}" stroke-width="1.8"/>'
        dy = 10 if where == "b" else -10
        return f'<line x1="{x - 8}" y1="{y + dy}" x2="{x + 8}" y2="{y + dy}" stroke="{INK}" stroke-width="1.8"/>'

    def many(x, y, where):
        if where in ("l", "r"):
            dx = -13 if where == "l" else 13
            return f'<path d="M {x + dx} {y} L {x} {y - 9} M {x + dx} {y} L {x} {y + 9}" stroke="{INK}" stroke-width="1.8" fill="none"/>'
        dy = -13 if where == "t" else 13
        return f'<path d="M {x} {y + dy} L {x - 9} {y} M {x} {y + dy} L {x + 9} {y}" stroke="{INK}" stroke-width="1.8" fill="none"/>'

    def rel(a, aw, bb, bw, at=0.5, bt=0.5, mid=False):
        (x1, y1), (x2, y2) = side(a, aw, at), side(bb, bw, bt)
        if mid:
            mx = (x1 + x2) / 2
            d = f"M {x1} {y1} L {mx} {y1} L {mx} {y2} L {x2} {y2}"
        else:
            d = f"M {x1} {y1} L {x2} {y2}"
        b.append(path(d, head=False, sw=1.6))
        b.append(one(x1, y1, aw))
        b.append(many(x2, y2, bw))

    rel("semesters", "r", "idea_checks", "l", 0.3, 0.12, True)
    rel("users", "r", "idea_checks", "l", 0.25, 0.45, True)
    rel("users", "r", "proposals", "l", 0.7, 0.4, True)
    rel("idea_checks", "b", "proposals", "t")
    rel("proposals", "b", "radar_collisions", "t")
    rel("idea_checks", "r", "idea_check_tags", "l", 0.12, 0.5, True)
    rel("idea_checks", "r", "check_matches", "l", 0.55, 0.3, True)
    rel("proposals", "r", "check_matches", "l", 0.3, 0.8, True)
    rel("tags", "l", "idea_check_tags", "r", 0.3, 0.5, True)
    rel("tags", "b", "tag_synonyms", "t")
    rel("corpus_projects", "l", "check_matches", "r", 0.2, 0.7, True)
    rel("corpus_projects", "l", "match_overrides", "r", 0.8, 0.5, True)
    rel("corpus_projects", "b", "corpus_project_tags", "t")
    rel("domains", "l", "corpus_projects", "r", 0.6, 0.55, True)
    rel("project_types", "l", "corpus_projects", "r", 0.6, 0.75, True)
    rel("project_types", "b", "problem_bank", "t")
    rel("problem_bank", "r", "problem_tags", "l", 0.3, 0.5, True)
    rel("syllabus_topics", "l", "corpus_project_topics", "r")
    rel("syllabus_topics", "r", "problem_topics", "l")
    x, y, w, h = E["idea_suggestions"]
    px, py, pw, ph = E["problem_bank"]
    gutter, y1, top = px + pw + 22, py + ph * 0.12, y + h + 34
    b.append(path(f"M {px + pw} {y1} L {gutter} {y1} L {gutter} {top} L {x + w * 0.3} {top} L {x + w * 0.3} {y + h}",
                  head=False, sw=1.6))
    b.append(one(px + pw, y1, "r"))
    b.append(many(x + w * 0.3, y + h, "b"))
    b.append(text(760, 1150, "Not drawn, to keep the lines readable: domains and project_types are also referenced by "
                            "idea_checks, problem_bank and idea_suggestions;", 16, 400, fill=NOTE))
    b.append(text(760, 1174, "tags by problem_tags; users by match_overrides.  UQ* = part of a composite unique key: "
                            "(group_code, semester_id) and (fingerprint, corpus_project_id).", 16, 400, fill=NOTE))
    b.append(text(760, 1198, "Bottom row: corpus_project_topics.project_id refers to corpus_projects; problem_topics and "
                            "problem_twist refer to problem_bank; suggestion_proof refers to idea_suggestions.", 16, 400, fill=NOTE))
    return svg(1510, 1225, "".join(b))


# ---------------------------------------------------------------- 6. Use case (portrait)
def actor(x, y, name):
    s = (f'<circle cx="{x}" cy="{y}" r="18" fill="#ffffff" stroke="{INK}" stroke-width="2"/>'
         f'<line x1="{x}" y1="{y + 18}" x2="{x}" y2="{y + 68}" stroke="{INK}" stroke-width="2"/>'
         f'<line x1="{x - 26}" y1="{y + 36}" x2="{x + 26}" y2="{y + 36}" stroke="{INK}" stroke-width="2"/>'
         f'<line x1="{x}" y1="{y + 68}" x2="{x - 22}" y2="{y + 100}" stroke="{INK}" stroke-width="2"/>'
         f'<line x1="{x}" y1="{y + 68}" x2="{x + 22}" y2="{y + 100}" stroke="{INK}" stroke-width="2"/>')
    return s + text(x, y + 124, name, 19, 700)


def oval(cx, cy, s, warm=False):
    style = (f'fill="{WARM}" stroke="{OCHRE}" stroke-dasharray="7 5"' if warm else f'fill="{SOFT}" stroke="{INK}"')
    return f'<ellipse cx="{cx}" cy="{cy}" rx="158" ry="31" {style} stroke-width="1.7"/>' + text(cx, cy, s, 17, 500)


def usecase():
    b = [text(500, 32, "Use case diagram", 26, 700)]
    b.append(box(150, 64, 700, 790, fill="#fbfcfa", rx=12))
    b.append(text(500, 92, "Origin", 20, 700))
    left = ["Register and sign in", "Try an idea on the map", "Check an idea", "View result", "Browse ideas",
            "Lock idea for group", "View Class Radar", "Download history"]
    right = ["Review locked ideas", "Correct a match", "Close the semester", "Download report", "Drop files in DB folder"]
    for i, u in enumerate(left):
        y = 150 + i * 84
        b.append(oval(330, y, u))
        b.append(line(98, 420, 172, y, head=False, sw=1.4))
    for i, u in enumerate(right):
        y = 190 + i * 92
        b.append(oval(670, y, u))
        b.append(line(902, 420, 828, y, head=False, sw=1.4))
    b.append(oval(670, 690, "Read the DB folder", warm=True))
    b.append(oval(670, 778, "Refresh ideas", warm=True))
    b.append(actor(70, 388, "Student"))
    b.append(actor(930, 388, "Supervisor"))
    b.append(box(560, 880, 240, 84, "Folder watcher and\ntimer (system)", size=17, weight=600, rx=2, dash="7 5"))
    b.append(line(640, 880, 640, 810, head=False, sw=1.4))
    b.append(line(720, 880, 720, 810, head=False, sw=1.4))
    b.append(path("M 930 540 L 930 1000 L 70 1000 L 70 540", dash="7 6"))
    b.append(text(500, 1026, "The supervisor can also do everything a student can (generalisation).", 17, 400, fill=NOTE))
    return svg(1000, 1050, "".join(b))


# ---------------------------------------------------------------- 7. Flowchart (portrait)
def term(x, y, w, s):
    return (f'<rect x="{x - w / 2}" y="{y - 26}" width="{w}" height="52" rx="26" fill="{INK}"/>'
            + text(x, y, s, 19, 700, fill="#ffffff"))


def step(x, y, w, s, h=56):
    return box(x - w / 2, y - h / 2, w, h, s, fill=SOFT, rx=3, size=17)


def ask(x, y, w, h, s):
    return (f'<path d="M {x} {y - h / 2} L {x + w / 2} {y} L {x} {y + h / 2} L {x - w / 2} {y} Z" fill="{WARM}" '
            f'stroke="{INK}" stroke-width="1.8"/>' + text(x, y, s, 17, 600))


def flowchart():
    X = 400
    b = [text(500, 32, "System flowchart: checking and locking an idea", 26, 700)]
    b.append(term(X, 84, 170, "Start"))
    b.append(line(X, 110, X, 140))
    b.append(step(X, 170, 360, "Sign in, open Check an idea"))
    b.append(line(X, 198, X, 226))
    b.append(f'<path d="M {X - 190} 228 L {X + 210} 228 L {X + 190} 290 L {X - 210} 290 Z" fill="#ffffff" stroke="{INK}" stroke-width="1.8"/>')
    b.append(text(X, 259, "Enter title, description,\ntags, domain, type", 17))
    b.append(line(X, 290, X, 318))
    b.append(ask(X, 370, 250, 100, "Input valid?"))
    b.append(line(X + 125, 370, 668, 370))
    b.append(label(600, 352, "No"))
    b.append(step(780, 370, 220, "Show field errors"))
    b.append(path(f"M 780 342 L 780 259 L {X + 202} 259"))
    b.append(line(X, 420, X, 448))
    b.append(label(X + 22, 434, "Yes", anchor="start"))
    b.append(step(X, 484, 420, "Fold synonyms, extract keywords,\nbuild fingerprint", h=68))
    b.append(line(X, 518, X, 546))
    b.append(step(X, 582, 420, "Score against past projects and\nlocked class ideas", h=68))
    b.append(line(X, 616, X, 644))
    b.append(step(X, 672, 420, "Apply remembered corrections"))
    b.append(line(X, 700, X, 728))
    b.append(step(X, 764, 420, "Band the score, flag claims,\nsave in one transaction", h=68))
    b.append(line(X, 798, X, 826))
    b.append(step(X, 854, 360, "Evaluation screen, then result page"))
    b.append(line(X, 882, X, 916))
    b.append(ask(X, 972, 280, 108, "Student locks\nfor the group?"))
    b.append(line(X - 140, 972, 168, 972))
    b.append(label(215, 954, "No"))
    b.append(term(100, 972, 130, "End"))
    b.append(line(X, 1026, X, 1060))
    b.append(label(X + 22, 1043, "Yes", anchor="start"))
    b.append(ask(X, 1120, 320, 116, "Already done, or group\nalready locked?"))
    b.append(line(X + 160, 1120, 668, 1120))
    b.append(label(610, 1102, "Yes"))
    b.append(step(780, 1120, 220, "Refuse and explain"))
    b.append(path("M 780 1148 L 780 1330 L 100 1330 L 100 1000"))
    b.append(line(X, 1178, X, 1210))
    b.append(label(X + 22, 1194, "No", anchor="start"))
    b.append(step(X, 1250, 440, "Save proposal, record clashes\n(triggers write the audit log)", h=68))
    b.append(path(f"M {X - 220} 1250 L 100 1250", head=False))
    return svg(1000, 1350, "".join(b))


DIAGRAMS = {
    "fig-sdlc": sdlc, "fig-architecture": architecture, "fig-dfd0": dfd0, "fig-dfd1": dfd1,
    "fig-er": er, "fig-usecase": usecase, "fig-flowchart": flowchart,
}

if __name__ == "__main__":
    with sync_playwright() as p:
        browser = p.chromium.launch(channel="chrome")
        page = browser.new_page(device_scale_factor=2)
        for name, fn in DIAGRAMS.items():
            s = fn()
            (OUT / f"{name}.svg").write_text(s, encoding="utf-8")
            page.set_content(f"<html><body style='margin:0'>{s}</body></html>")
            page.query_selector("svg").screenshot(path=str(OUT / f"{name}.png"))
            print("drew", name)
        browser.close()
