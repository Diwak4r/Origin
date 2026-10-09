"""One-time patch: brings tools/diagrams.py in line with Origin v3 (DB folder, syllabus tables, no live engine)."""
from pathlib import Path

p = Path(__file__).resolve().parent.parent / "diagrams.py"
s = p.read_text(encoding="utf-8")


def sub(old, new):
    global s
    if old not in s:
        raise SystemExit("missing: " + old[:80])
    s = s.replace(old, new, 1)


# ---- SDLC
sub('("Increment 4", "Ideas, background engine")', '("Increment 4", "Ideas, DB-folder ingest")')
sub('("Increment 5", "Supervisor desk, tests, report")', '("Increment 5", "Syllabus map, tests, report")')

# ---- Architecture
sub('"JSP pages as HTML  ·  origin.css  ·  origin.js"', '"JSP pages as HTML  ·  origin.css  ·  map.js, evaluating.js"')
sub('"Auth · Check\\nExplore · Admin\\nAppListener"', '"Auth · Check · Explore\\nAdmin · Peek (JSON)\\nAppListener"')
sub('"11 pages\\n3 fragments\\nJSTL tags"', '"12 pages\\n3 fragments\\nJSTL tags"')
sub('for i, s in enumerate(["GapService", "AuthService"]):\n        b.append(box(50 + i * 195, 574, 180, 46, s, fill=SOFT, size=16))',
    'for i, s in enumerate(["IngestService", "SyllabusService", "GapService"]):\n        b.append(box(50 + i * 195, 574, 180, 46, s, fill=SOFT, size=16))')
sub('"Engine: SimilarityEngine, three Scorers,\\nVocabulary, ClaimDetector"', '"Engine: SimilarityEngine, three Scorers, MatchIndex,\\nTermStats, Vocabulary, ClaimDetector"')
sub('b.append(text(820, 580, "2 worker threads\\nmaintenance timer\\nevery 10 minutes", 17))\n    b.append(text(820, 672, "refine checks,\\nrefresh ideas", 16, 600, fill=OCHRE))',
    'b.append(text(820, 566, "DB folder watcher\\nevery 5 seconds\\nmaintenance timer\\nevery 10 minutes", 17))\n    b.append(text(820, 672, "read files,\\nrefresh ideas", 16, 600, fill=OCHRE))')
sub('"19 tables (3NF) · 4 views\\n6 triggers · fn_band · 2 procedures"', '"25 tables (3NF) · 4 views\\n6 triggers · fn_band · 2 procedures"')
sub('b.append(text(820, 900, "Decision engine\\n(external, optional)", 18, 700))\n    b.append(text(820, 978, "TypeSafe Jev over HTTPS\\nanswers cached in\\nengine_cache", 16))\n    b.append(line(820, 720, 820, 858))\n    b.append(label(835, 790, "HTTPS", 16, anchor="start"))',
    'b.append(text(820, 900, "DB folder\\n(past-project files)", 18, 700))\n    b.append(text(820, 978, "CSV · TXT · XLSX · DOCX\\nread with java.io and\\njava.util.zip", 16))\n    b.append(line(820, 858, 820, 722))\n    b.append(label(835, 790, "files", 16, anchor="start"))')

# ---- DFD level 0
sub('b.append(box(390, 540, 220, 80, "Decision engine", size=19, weight=700, rx=2))', 'b.append(box(390, 540, 220, 80, "DB folder", size=19, weight=700, rx=2))')
sub('    b.append(line(460, 422, 460, 538))\n    b.append(label(445, 480, "typed questions", anchor="end"))\n    b.append(line(540, 538, 540, 424))\n    b.append(label(555, 480, "probabilities", anchor="start"))',
    '    b.append(line(500, 538, 500, 426))\n    b.append(label(515, 482, "past-project files", anchor="start"))')
sub('"clashes, statistics,\\nreport"', '"clashes, ingest log,\\nreport"')

# ---- DFD level 1: process 6.0 reads the DB folder instead of asking an engine
sub('b.append(box(1300, 880, 180, 80, "Decision\\nengine", size=18, weight=700, rx=2))', 'b.append(box(1300, 880, 180, 80, "DB folder\\n(files)", size=18, weight=700, rx=2))')
sub('b.append(proc(970, 770, "6.0", "Refine in\\nbackground"))', 'b.append(proc(970, 770, "6.0", "Read the\\nDB folder"))')
sub('("D2", "Corpus + tags", 200)', '("D2", "Corpus + topics", 200)')
sub('("D7", "Engine cache", 900)', '("D7", "Ingest log", 900)')
sub('    b.append(line(968, 790, 864, 662))\n    b.append(label(930, 706, "read,\\nupdate"))\n    b.append(line(968, 850, 864, 922))\n    b.append(label(915, 902, "cache"))\n    b.append(line(1190, 800, 1298, 900))\n    b.append(label(1256, 832, "questions"))\n    b.append(line(1300, 940, 1192, 845))\n    b.append(label(1225, 920, "answers"))',
    '    b.append(line(968, 780, 864, 232))\n    b.append(label(906, 600, "add\\nprojects"))\n    b.append(line(968, 850, 864, 922))\n    b.append(label(915, 902, "log"))\n    b.append(line(1300, 930, 1192, 845))\n    b.append(label(1262, 912, "files"))')
sub('b.append(label(1256, 440, "approve,\\narchive"))', 'b.append(label(1256, 440, "approve,\\narchive"))')

# ---- Use case: the system reads the DB folder; a student tries ideas on the map
sub('left = ["Register and sign in", "Check an idea", "View result", "Explore gap map", "Browse ideas",\n            "Lock idea for group", "View Class Radar", "Download history"]',
    'left = ["Register and sign in", "Try an idea on the map", "Check an idea", "View result", "Browse ideas",\n            "Lock idea for group", "View Class Radar", "Download history"]')
sub('b.append(oval(670, 690, "Refine verdict", warm=True))', 'b.append(oval(670, 690, "Read the DB folder", warm=True))')
sub('"Timer and decision\\nengine (system)"', '"Folder watcher and\\ntimer (system)"')
sub('right = ["Review locked ideas", "Correct a match", "Close the semester", "Download report", "Read statistics"]',
    'right = ["Review locked ideas", "Correct a match", "Close the semester", "Download report", "Drop files in DB folder"]')

# ---- Flowchart: no engine branch; the evaluation screen sits between the save and the result
sub('b.append(step(X, 764, 420, "Band the score, flag claims,\\nsave in one transaction", h=68))', 'b.append(step(X, 764, 420, "Band the score, flag claims,\\nsave in one transaction", h=68))')
sub('b.append(step(X, 854, 300, "Show result page"))\n    b.append(line(X + 150, 854, 668, 854))\n    b.append(ask(780, 854, 220, 96, "Engine on?"))\n    b.append(line(780, 902, 780, 948))\n    b.append(label(796, 924, "Yes", anchor="start"))\n    b.append(step(780, 992, 260, "Background thread:\\nask, blend, update", h=80))\n',
    'b.append(step(X, 854, 360, "Evaluation screen, then result page"))\n')
p.write_text(s, encoding="utf-8")
print("diagrams patched (ER is rewritten separately)")
