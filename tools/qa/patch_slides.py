"""One-time patch: the slide deck of Origin v3."""
from pathlib import Path

p = Path(__file__).resolve().parent.parent / "build_slides.py"
s = p.read_text(encoding="utf-8")


def sub(old, new):
    global s
    if old not in s:
        raise SystemExit("missing: " + old[:100])
    s = s.replace(old, new, 1)


sub('''        "It also shows where the open ground is: a gap map of 12 domains x 6 project types and ranked ideas built from real problems in Nepal.",
        "Built with Java Servlets and JSP on Apache Tomcat, with a MySQL database that keeps itself current.",''',
    '''        "A Syllabus Map of 41 topics reacts as you type, and the Ideas page suggests real problems built from topics nobody has used.",
        "Past projects load themselves from files dropped into a folder. Built with Java Servlets and JSP on Tomcat and MySQL.",''')
sub('("To build a web application that checks a project idea against past and current projects, shows the gaps and suggests real problems to build.", 1),',
    '("To build a web application that loads past projects from files, checks an idea against them and current projects, shows untouched syllabus topics and suggests real problems to build.", 1),')
sub('("Build the gap map and the idea generator.", 1),', '("Build the Syllabus Map and the idea suggester.", 1),')
sub('("Implement Class Radar, supervisor review, semester archiving and background refinement.", 1),',
    '("Implement Class Radar, supervisor review, semester archiving and the DB folder.", 1),')
sub('"Idea check against the past-project corpus and this semester\'s locked ideas, with a score split into tags, words, type and meaning.",',
    '"Idea check against the past-project corpus and this semester\'s locked ideas, with a score split into tags, words and type, told as a story.",')
sub('"Gap map and ranked idea suggestions; supervisor review, corrections and semester archiving. Roles: Student and Supervisor.",',
    '"Syllabus Map, gap map and ranked idea suggestions; DB-folder import of CSV, TXT, XLSX and DOCX; supervisor review and semester archiving.",')
sub('"Not included: checking finished reports or code for copied text, mobile app, multi-college use.",',
    '"Not included: PDF files, checking finished reports or code for copied text, mobile app, multi-college use.",')
sub('"**Economic: all tools are free; the optional decision engine costs a fraction of a cent per question, and answers are cached.",',
    '"**Economic: all tools are free; no paid service is needed.",')
sub('["Build and test", "Maven 3.9, JUnit 5"],', '["Build and test", "Maven 3.9, JUnit 5, Chrome DevTools driver (Node)"],')
sub('["Optional service", "TypeSafe Jev decision engine over HTTPS"],',
    '["File reading", "java.io and java.util.zip for CSV, TXT, XLSX and DOCX (no extra library)"],')
sub('''        "Working system: idea check, gap map, ideas, Class Radar and supervisor desk.",
        "Renamed library idea caught at 74%; rules agree with human labels on 14 of 15 pairs.",
        "Database does work on its own: 6 triggers, 4 views, 1 function, 2 procedures.",
        "84 ranked suggestions from 42 real problems; 5 marked as taken by locked groups.",
        "35 automated tests pass; every page answers in under 110 ms.",''',
    '''        "Working system: Syllabus Map, idea check, ideas with proof, Class Radar, DB-folder import and supervisor desk.",
        "Renamed library idea caught at 62%; the formula agrees with human labels on 22 of 25 pairs, as often as the old one.",
        "DB folder: a real Excel file and a CSV were read in 8 seconds and the map updated on its own.",
        "84 suggestions from 42 real problems, each with proof; 7 taken by locked groups; 25 tables, 6 triggers, 4 views, 2 procedures.",
        "84 automated tests pass; every page answers in under 0.4 s.",''')
sub('"[6] D. Almeida, \\"Introducing System One Models and Jev,\\" TypeSafe AI, Sep. 2026.",',
    '"[6] C. D. Manning, P. Raghavan, and H. Schutze, Introduction to Information Retrieval. Cambridge Univ. Press, 2008.",')

# one new slide after the ER diagram: the Syllabus Map
sub('''    for sl in s:
        set_dates(sl)
    prs.save(out)''',
    '''    # new slide after the ER diagram: the Syllabus Map (a real screenshot of the running system)
    er_slide, hw_slide = s[14], s[15]
    new = prs.slides.add_slide(hw_slide.slide_layout)
    new.shapes.title.text = "Syllabus Map"
    for ph in list(new.placeholders):
        if ph.placeholder_format.idx not in (0, 12):
            ph._element.getparent().remove(ph._element)
    title = new.shapes.title
    picture(new, IMG / "f51-home.png", top=min(3.4, max(2.6, (title.top + title.height) / 360000 + 0.1)), bottom=17.3)
    src_date = date_box(er_slide)
    if src_date is not None and date_box(new) is None:
        new.shapes._spTree.append(copy.deepcopy(src_date._element))
        dst = date_box(new)
        dst.left, dst.top, dst.width, dst.height = src_date.left, src_date.top, src_date.width, src_date.height
    ids = prs.slides._sldIdLst
    entry = ids[-1]
    ids.remove(entry)
    ids.insert(15, entry)

    for sl in prs.slides:
        set_dates(sl)
    prs.save(out)''')
p.write_text(s, encoding="utf-8")
print("slides patched")
