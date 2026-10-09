"""One-time patch: the ER diagram of Origin v3 (25 tables)."""
from pathlib import Path

p = Path(__file__).resolve().parent.parent / "diagrams.py"
s = p.read_text(encoding="utf-8")


def sub(old, new):
    global s
    if old not in s:
        raise SystemExit("missing: " + old[:90])
    s = s.replace(old, new, 1)


sub("(19 tables, crow's foot notation)", "(25 tables, crow's foot notation)")
sub('ent("corpus_projects", C[3], 368, [("id", "PK"), ("title", ""), ("year", ""), ("domain_id", "FK"),\n                                       ("type_id", "FK"), ("source", "")])',
    'ent("corpus_projects", C[3], 368, [("id", "PK"), ("title", ""), ("year", ""), ("domain_id", "FK"),\n                                       ("type_id", "FK"), ("tech", ""), ("source", "")])')
sub('    ent("problem_tags", C[5], 390, [("problem_id", "PK FK"), ("tag_id", "PK FK")])\n',
    '''    ent("problem_tags", C[5], 390, [("problem_id", "PK FK"), ("tag_id", "PK FK")])
    # v3: DB-folder ingest and the syllabus layer
    ent("ingest_log", C[0], 745, [("id", "PK"), ("file_name", ""), ("file_size", ""), ("status", ""), ("rows_added", "")])
    ent("corpus_project_topics", C[1], 900, [("project_id", "PK FK"), ("topic_id", "PK FK")])
    ent("syllabus_topics", C[2], 900, [("id", "PK"), ("course", ""), ("unit_no", ""), ("name", "UQ"), ("crud", "")])
    ent("problem_topics", C[3], 900, [("problem_id", "PK FK"), ("topic_id", "PK FK")])
    ent("problem_twist", C[4], 900, [("problem_id", "PK FK"), ("twist", "")])
    ent("suggestion_proof", C[5], 900, [("suggestion_id", "PK FK"), ("pair_uses", ""), ("units", ""), ("crud_only", ""),
                                        ("topic_list", "")])
''')
sub('    x, y, w, h = E["idea_suggestions"]\n    px, py, pw, ph = E["problem_bank"]',
    '    rel("syllabus_topics", "l", "corpus_project_topics", "r")\n    rel("syllabus_topics", "r", "problem_topics", "l")\n    x, y, w, h = E["idea_suggestions"]\n    px, py, pw, ph = E["problem_bank"]')
sub('    b.append(text(760, 880, "Not drawn, to keep the lines readable: domains and project_types are also referenced by "\n                            "idea_checks, problem_bank and idea_suggestions;", 16, 400, fill=NOTE))\n    b.append(text(760, 904, "tags by problem_tags; users by match_overrides.  UQ* = part of a composite unique key: "\n                            "(group_code, semester_id) and (fingerprint, corpus_project_id).", 16, 400, fill=NOTE))\n    return svg(1510, 925, "".join(b))',
    '''    b.append(text(760, 1150, "Not drawn, to keep the lines readable: domains and project_types are also referenced by "
                            "idea_checks, problem_bank and idea_suggestions;", 16, 400, fill=NOTE))
    b.append(text(760, 1174, "tags by problem_tags; users by match_overrides.  UQ* = part of a composite unique key: "
                            "(group_code, semester_id) and (fingerprint, corpus_project_id).", 16, 400, fill=NOTE))
    b.append(text(760, 1198, "Bottom row: corpus_project_topics.project_id refers to corpus_projects; problem_topics and "
                            "problem_twist refer to problem_bank; suggestion_proof refers to idea_suggestions.", 16, 400, fill=NOTE))
    return svg(1510, 1225, "".join(b))''')
p.write_text(s, encoding="utf-8")
print("ER patched")
