"""Builds the Origin final report from the college template (BIT_Final_Report_Template.docx).

Usage: py tools/build_report.py <template.docx> <out.docx>
Word then updates the table of contents and the page numbers (tools/finish_report.ps1).
"""
import copy
import re
import sys
from pathlib import Path

from docx import Document
from docx.enum.section import WD_ORIENT, WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

ROOT = Path(__file__).resolve().parent.parent
DIAG = ROOT / "docs" / "diagrams"
IMG = ROOT / "docs" / "report-img"

TITLE = "Origin: A Project Gap and Similarity Finder"
STUDENTS = ["Sandesh Dotel", "Bhumika Karki", "Diwakar Ray Yadav"]
MONTH = "September, 2026"

FIGURES = []   # (number, title)
TABLES = []


# ------------------------------------------------------------------ low-level helpers

def set_font(run, size=12, bold=None, italic=None, name="Times New Roman"):
    run.font.name = name
    run.font.size = Pt(size)
    rpr = run._element.get_or_add_rPr()
    fonts = rpr.find(qn("w:rFonts"))
    if fonts is None:
        fonts = OxmlElement("w:rFonts")
        rpr.insert(0, fonts)
    for k in ("w:ascii", "w:hAnsi", "w:cs", "w:eastAsia"):
        fonts.set(qn(k), name)
    if bold is not None:
        run.bold = bold
    if italic is not None:
        run.italic = italic


def add_runs(p, text, size=12, name="Times New Roman"):
    """Adds text with **bold** and ~~italic~~ spans. (Underscores are left alone: SQL names use them.)"""
    for part in re.split(r"(\*\*[^*]+\*\*|~~[^~]+~~)", text):
        if not part:
            continue
        if part.startswith("**"):
            set_font(p.add_run(part[2:-2]), size, bold=True, name=name)
        elif part.startswith("~~"):
            set_font(p.add_run(part[2:-2]), size, italic=True, name=name)
        else:
            set_font(p.add_run(part), size, name=name)


def fmt(p, align=WD_ALIGN_PARAGRAPH.JUSTIFY, first=True, after=6, before=0, line=1.5, keep=False):
    pf = p.paragraph_format
    pf.alignment = align
    pf.first_line_indent = Cm(1.27) if first else None
    pf.space_after = Pt(after)
    pf.space_before = Pt(before)
    pf.line_spacing = line
    if keep:
        pf.keep_with_next = True


class Writer:
    def __init__(self, doc):
        self.doc = doc

    def p(self, text, first=True, align=WD_ALIGN_PARAGRAPH.JUSTIFY, size=12, after=6):
        para = self.doc.add_paragraph()
        add_runs(para, text, size)
        fmt(para, align, first, after)
        return para

    def h1(self, text):
        para = self.doc.add_paragraph(style="Heading 1")
        run = para.add_run(text.upper())
        set_font(run, 16, bold=True)
        pf = para.paragraph_format
        pf.alignment = WD_ALIGN_PARAGRAPH.CENTER
        pf.page_break_before = True
        pf.space_after = Pt(18)
        pf.keep_with_next = True
        return para

    def h2(self, text):
        para = self.doc.add_paragraph(style="Heading 2")
        set_font(para.add_run(text), 14, bold=True)
        pf = para.paragraph_format
        pf.alignment = WD_ALIGN_PARAGRAPH.LEFT
        pf.space_before = Pt(12)
        pf.space_after = Pt(12)
        pf.keep_with_next = True
        return para

    def h3(self, text):
        para = self.doc.add_paragraph(style="Heading 3")
        set_font(para.add_run(text), 12, bold=True)
        pf = para.paragraph_format
        pf.alignment = WD_ALIGN_PARAGRAPH.LEFT
        pf.space_before = Pt(8)
        pf.space_after = Pt(6)
        pf.keep_with_next = True
        return para

    def label(self, text):
        para = self.doc.add_paragraph()
        add_runs(para, f"**{text}**")
        fmt(para, WD_ALIGN_PARAGRAPH.LEFT, False, 4, keep=True)
        return para

    def bullets(self, items):
        for item in items:
            para = self.doc.add_paragraph()
            ppr = para._p.get_or_add_pPr()
            num = OxmlElement("w:numPr")
            ilvl = OxmlElement("w:ilvl")
            ilvl.set(qn("w:val"), "0")
            nid = OxmlElement("w:numId")
            nid.set(qn("w:val"), "1")
            num.append(ilvl)
            num.append(nid)
            ppr.append(num)
            add_runs(para, item)
            pf = para.paragraph_format
            pf.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
            pf.left_indent = Cm(1.27)
            pf.first_line_indent = Cm(-0.63)
            pf.space_after = Pt(4)
            pf.line_spacing = 1.5

    def figure(self, number, path, title, width_cm=15.0, max_height_cm=None):
        para = self.doc.add_paragraph()
        para.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.CENTER
        para.paragraph_format.keep_with_next = True
        para.paragraph_format.space_before = Pt(6)
        run = para.add_run()
        if max_height_cm:
            from PIL import Image
            with Image.open(path) as im:
                w, h = im.size
            width_cm = min(width_cm, max_height_cm * w / h)
        run.add_picture(str(path), width=Cm(width_cm))
        cap = self.doc.add_paragraph()
        set_font(cap.add_run(f"Figure {number}: {title}"), 11, bold=True, italic=True)
        fmt(cap, WD_ALIGN_PARAGRAPH.CENTER, False, 12)
        FIGURES.append((number, title))

    def table(self, number, title, headers, rows, widths, size=10, header_fill="D6E3D9"):
        cap = self.doc.add_paragraph()
        set_font(cap.add_run(f"Table {number}: {title}"), 11, bold=True, italic=True)
        fmt(cap, WD_ALIGN_PARAGRAPH.CENTER, False, 6, before=6, keep=True)
        TABLES.append((number, title))
        t = self.doc.add_table(rows=1, cols=len(headers))
        t.style = "Table Grid" if "Table Grid" in [s.name for s in self.doc.styles] else None
        t.alignment = WD_TABLE_ALIGNMENT.CENTER
        borders(t)
        for i, h in enumerate(headers):
            cell = t.rows[0].cells[i]
            cell.text = ""
            add_runs(cell.paragraphs[0], f"**{h}**", size)
            shade(cell, header_fill)
        for row in rows:
            cells = t.add_row().cells
            for i, v in enumerate(row):
                cells[i].text = ""
                fill = None
                if isinstance(v, tuple):
                    v, fill = v
                add_runs(cells[i].paragraphs[0], str(v), size)
                if fill:
                    shade(cells[i], fill)
        for row in t.rows:
            for i, w in enumerate(widths):
                row.cells[i].width = Cm(w)
            for c in row.cells:
                for para in c.paragraphs:
                    para.paragraph_format.space_after = Pt(2)
                    para.paragraph_format.line_spacing = 1.0
        repeat_header(t.rows[0])
        for row in t.rows:
            trpr = row._tr.get_or_add_trPr()
            cs = OxmlElement("w:cantSplit")
            cs.set(qn("w:val"), "true")
            trpr.append(cs)
        if len(t.rows) <= 13:
            # a short table stays on one page: every row keeps with the next one
            for row in list(t.rows)[:-1]:
                for c in row.cells:
                    for para in c.paragraphs:
                        para.paragraph_format.keep_with_next = True
        spacer = self.doc.add_paragraph()
        spacer.paragraph_format.space_after = Pt(4)
        return t

    def code(self, lines):
        for i, line in enumerate(lines):
            para = self.doc.add_paragraph()
            set_font(para.add_run(line if line else " "), 10, name="Courier New")
            pf = para.paragraph_format
            pf.space_after = Pt(0)
            pf.line_spacing = 1.0
            pf.left_indent = Cm(0.6)
        self.doc.add_paragraph().paragraph_format.space_after = Pt(2)


def shade(cell, fill):
    tcpr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), fill)
    tcpr.append(shd)


def borders(table):
    tbl = table._tbl
    tblpr = tbl.tblPr
    b = OxmlElement("w:tblBorders")
    for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
        e = OxmlElement(f"w:{edge}")
        e.set(qn("w:val"), "single")
        e.set(qn("w:sz"), "4")
        e.set(qn("w:space"), "0")
        e.set(qn("w:color"), "555555")
        b.append(e)
    tblpr.append(b)


def repeat_header(row):
    trpr = row._tr.get_or_add_trPr()
    h = OxmlElement("w:tblHeader")
    h.set(qn("w:val"), "true")
    trpr.append(h)


def replace_text(paragraph, new):
    runs = paragraph.runs
    if not runs:
        paragraph.add_run(new)
        return
    runs[0].text = new
    for r in runs[1:]:
        r.text = ""


def sect_of(el):
    ppr = el.find(qn("w:pPr"))
    return None if ppr is None else ppr.find(qn("w:sectPr"))


# ------------------------------------------------------------------ build

def build(template, out):
    doc = Document(template)
    body = doc.element.body
    kids = list(body.iterchildren())

    # Locate the three paragraph-level section breaks: cover | guidelines | front matter | body
    breaks = [i for i, el in enumerate(kids) if el.tag == qn("w:p") and sect_of(el) is not None]
    cover_end, guide_end, front_end = breaks[0], breaks[1], breaks[2]

    # 1. Remove the formatting-guidelines section (the template says to remove it before submission)
    for el in kids[cover_end + 1: guide_end + 1]:
        body.remove(el)
    # 2. Remove the template's placeholder chapters; ours are written below
    kids = list(body.iterchildren())
    front_end = [i for i, el in enumerate(kids) if el.tag == qn("w:p") and sect_of(el) is not None][1]
    for el in kids[front_end + 1:]:
        if el.tag != qn("w:sectPr"):
            body.remove(el)

    # Front matter pages in lower-case roman numerals, starting at i
    fm_sect = sect_of(list(body.iterchildren())[front_end])
    pg = fm_sect.find(qn("w:pgNumType"))
    if pg is None:
        pg = OxmlElement("w:pgNumType")
        fm_sect.append(pg)
    pg.set(qn("w:fmt"), "lowerRoman")
    pg.set(qn("w:start"), "1")

    fill_cover_and_front(doc)
    w = Writer(doc)
    chapters(w)
    fill_lists(doc)
    doc.save(out)
    print("saved", out, len(FIGURES), "figures", len(TABLES), "tables")


def fill_cover_and_front(doc):
    names = iter(STUDENTS)
    for p in doc.paragraphs:
        t = p.text.strip()
        if "Place Your Project Title Here" in t:
            replace_text(p, f"“{TITLE}”")
        elif re.match(r"Student Name \d \[Roll No\.\]", t):
            replace_text(p, f"{next(names)} [Roll No.]")
        elif t == "[Month], [Year]":
            replace_text(p, MONTH)
        elif t.startswith("The Major Project entitled"):
            replace_text(p, f"The Major Project entitled “{TITLE}”, submitted by {STUDENTS[0]}, {STUDENTS[1]}, "
                            f"and {STUDENTS[2]} in partial fulfillment of the requirements for the degree of "
                            f"“Bachelor of Information Technology” has been accepted as a bona fide record of work "
                            f"carried out by them in the department.")
        elif t.startswith("[Student Name 1]"):
            replace_text(p, STUDENTS[0])
        elif t.startswith("[Student Name 2]"):
            replace_text(p, STUDENTS[1])
        elif t.startswith("[Student Name 3]"):
            replace_text(p, STUDENTS[2])

    # Acknowledgement: same people as the template, in our own words
    ack = [p for p in doc.paragraphs if p.text.startswith("We would like to express our sincere gratitude")][0]
    replace_text(ack, "We thank our project supervisor, [Supervisor Name], for guiding this project and for the advice "
                      "that shaped it more than any other: build something you can explain line by line. The review comments before the proposal defense pushed Origin to use its database more "
                      "actively, and the final system is better for it.")
    nxt = ack._p.getnext()
    from docx.text.paragraph import Paragraph
    p2 = Paragraph(nxt, ack._parent)
    replace_text(p2, "We are grateful to Er. Bimal Sharma, Head of Department, Department of IT, Computer and Electronics, "
                     "and Er. Aashish Lamsal, Project Coordinator, for the laboratory time, the schedule and the "
                     "feedback sessions that kept the project on track.")
    p3 = Paragraph(p2._p.getnext(), ack._parent)
    replace_text(p3, "We also thank the faculty members of the Department of IT, Computer and Electronics, Himalayan "
                     "Whitehouse International College, for their support throughout the semester.")
    p4 = Paragraph(p3._p.getnext(), ack._parent)
    replace_text(p4, "Our classmates in the Bachelor of Information Technology programme discussed their own project ideas "
                     "with us, and those discussions gave us the idea for Class Radar. Our families gave us the time "
                     "to finish the work. Thank you.")

    # Abstract
    ab = [p for p in doc.paragraphs if p.text.startswith("[Write the abstract here.")][0]
    replace_text(ab, ABSTRACT)
    for p in doc.paragraphs:
        if p.text.startswith("Keywords:"):
            p.clear()
            set_font(p.add_run("Keywords: "), 12, bold=True)
            set_font(p.add_run(KEYWORDS), 12, italic=True)
        if p.text.startswith("Note: Include 5–6 keywords"):
            p._p.getparent().remove(p._p)


def fill_lists(doc):
    tables = doc.tables
    lof, lot, abbr = tables[0], tables[1], tables[2]
    for t, items, prefix in ((lof, FIGURES, "Figure"), (lot, TABLES, "Table")):
        for row in list(t.rows)[1:]:
            t._tbl.remove(row._tr)
        for num, title in items:
            cells = t.add_row().cells
            for i, v in enumerate((f"{prefix} {num}", title, "")):
                cells[i].text = ""
                set_font(cells[i].paragraphs[0].add_run(v), 11)
                cells[i].width = Cm((2.8, 10.4, 2.0)[i])
        for row in t.rows:
            for i, wcm in enumerate((2.8, 10.4, 2.0)):
                row.cells[i].width = Cm(wcm)
    for row in list(abbr.rows):
        abbr._tbl.remove(row._tr)
    for a, full in ABBREVIATIONS:
        cells = abbr.add_row().cells
        cells[0].text = ""
        cells[1].text = ""
        set_font(cells[0].paragraphs[0].add_run(a), 12, bold=True)
        set_font(cells[1].paragraphs[0].add_run(full), 12)


# ------------------------------------------------------------------ content

ABSTRACT = (
    "Every semester, student groups in project-based programmes propose ideas that repeat earlier projects or clash "
    "with a classmate's idea, and the overlap is usually found late. Origin is a web application that checks a project "
    "idea at proposal time. A student enters a title, a description, tags, a domain and a project type. Origin scores "
    "the idea against a corpus of past projects and against the ideas other groups have locked this semester, using "
    "three explainable signals: shared tags, shared words and the same project type. A synonym table catches renamed "
    "ideas and a claim detector flags buzzwords that the description does not support. Beyond the check, Origin draws a "
    "gap map of domains crossed with project types and turns 42 real problems from Nepal into ranked project "
    "suggestions. The MySQL database keeps itself current through triggers, views and stored procedures, and a "
    "background thread asks an external decision engine to refine the closest matches. The system is built with Java "
    "Servlets and Jakarta Server Pages on Apache Tomcat with a MySQL database. All 35 automated tests pass, the engine agrees with human labels on 14 of "
    "15 pairs, and a full check completes in about 100 milliseconds."
)
KEYWORDS = "project originality, similarity scoring, gap analysis, Java Servlets, MySQL triggers, Jaccard index"

ABBREVIATIONS = sorted([
    ("AI", "Artificial Intelligence"), ("API", "Application Programming Interface"),
    ("BIT", "Bachelor of Information Technology"), ("CSRF", "Cross-Site Request Forgery"),
    ("CSS", "Cascading Style Sheets"), ("CSV", "Comma-Separated Values"), ("DFD", "Data Flow Diagram"),
    ("ER", "Entity Relationship"), ("GB", "Gigabyte"), ("HMAC", "Hash-based Message Authentication Code"),
    ("HTML", "HyperText Markup Language"), ("HTTP", "HyperText Transfer Protocol"),
    ("HTTPS", "HyperText Transfer Protocol Secure"), ("IDE", "Integrated Development Environment"),
    ("IEEE", "Institute of Electrical and Electronics Engineers"), ("IoT", "Internet of Things"),
    ("JDBC", "Java Database Connectivity"), ("JDK", "Java Development Kit"), ("JSON", "JavaScript Object Notation"),
    ("JSP", "Jakarta Server Pages"), ("JSTL", "Jakarta Standard Tag Library"),
    ("PBKDF2", "Password-Based Key Derivation Function 2"), ("RAM", "Random Access Memory"),
    ("SDLC", "Software Development Life Cycle"), ("SHA", "Secure Hash Algorithm"),
    ("SQL", "Structured Query Language"), ("TC", "Test Case"), ("URL", "Uniform Resource Locator"),
    ("WAR", "Web Application Archive"),
], key=lambda x: x[0].lower())


def chapters(w):
    ch1(w)
    ch2(w)
    ch3(w)
    ch4(w)
    ch5(w)
    ch6(w)
    references(w)
    annex(w)


def ch1(w):
    w.h1("Chapter 1: Introduction")
    w.h2("1.1 Background")
    w.p("A Bachelor of Information Technology student builds a software project almost every semester. Each project "
        "starts with a proposal, and the proposal starts with an idea. Over the years a college collects hundreds of these "
        "projects, and most of them sit in shelves, shared drives and the memory of supervisors. The same kinds of project "
        "keep coming back: library systems, attendance systems, hospital systems, online shops and hotel booking sites. "
        "In a sample of 71 typical BIT projects that we compiled for this work, 40 are web applications and 15 are in "
        "education alone.")
    w.p("Tools that check originality already exist, but they check finished writing. Turnitin compares submitted text "
        "with a large collection of documents [1], and code-similarity tools such as MOSS compare finished source code [2]. "
        "Neither helps a student at the moment that matters most, which is the week they choose what to build. At that "
        "point there is no text to compare, only an idea: a title, a few sentences and a sense of what the system does.")
    w.p("Origin is built for that moment. It compares the structure of an idea with past projects and with this "
        "semester's locked ideas, explains every number it shows, and then goes one step further: it shows where the open "
        "ground is and suggests real problems that no group has built yet.")

    w.h2("1.2 Problem Statement")
    w.p("Students choose project ideas without a reliable way to see what has already been built at their college or "
        "what their classmates are building now. The result is a steady stream of near-identical proposals, late clashes "
        "between groups, and supervisors who have to remember years of past titles to catch them.")
    w.p("Repetition is not the only problem. Two groups in the same class can also pick the same idea in the same week "
        "without knowing it, and the clash appears at the proposal defense or, worse, at the mid-term presentation. By then "
        "both groups have spent weeks on it.")
    w.p("The Project-IV course gives 10 of its 60 internal marks to title identification and proposal writing, 20 to "
        "the mid-term presentation and 30 to the final submission. The idea is chosen first and everything else is built "
        "on it: the proposal, the diagrams, the database and the code. A group that learns at the mid-term that its idea "
        "was built two years ago, or that another group is building the same thing, has to defend it or start again with "
        "half the semester gone.")
    w.p("Three things make the problem hard to solve with existing tools. First, a repeated idea rarely repeats its "
        "wording. \"Library Management System\" becomes \"Book Depot Portal\", and a word-for-word checker misses it. "
        "Second, students pad old ideas with words like \"AI\" and \"prediction\" to make them look new, even when nothing "
        "in the description supports the claim. Third, telling a student that an idea is taken is only half an answer. "
        "Without knowing which areas are still empty, the student simply picks the next crowded idea.")
    w.p("A shared spreadsheet of past titles would help a little, but it cannot tell a student that \"Book Depot "
        "Portal\" and \"Library Management System\" are the same project, it cannot show a classmate's idea on the day "
        "it is chosen, and it cannot say which areas nobody has touched. Those things need software that looks at what "
        "an idea does, not only at what it is called.")
    w.p("At the same time, the problems around the students stay unsolved. Households in Kathmandu wait for private water "
        "tankers without knowing their place in the queue, farmers sell vegetables without knowing that day's wholesale "
        "price at Kalimati, and rural health posts run out of essential medicines before the district store notices. A "
        "three-person team can build a working system for any of these in one semester, yet our sample of past projects "
        "has only 2 projects each in agriculture, in environment and disaster, and in governance.")
    w.p("The people affected are students, who lose time; supervisors, who carry the whole checking load; and the "
        "department, which receives the same few projects year after year while real local problems go unsolved.")

    w.h2("1.3 Objectives")
    w.h3("1.3.1 General Objective")
    w.p("To design and develop Origin, a web application that checks a student project idea against past projects and "
        "this semester's locked ideas at proposal time, shows where the unexplored areas are, and suggests real problems "
        "worth building, with a database that keeps its own records current.")
    w.h3("1.3.2 Specific Objectives")
    w.bullets([
        "To analyse how BIT project ideas repeat and to study existing originality and plagiarism tools.",
        "To design a layered web architecture and a normalised MySQL schema whose triggers, views and stored procedures "
        "keep the data current without manual upkeep.",
        "To develop an explainable similarity engine that compares an idea with past projects and locked class ideas and "
        "resists renaming and buzzword padding.",
        "To develop a gap map and an idea generator that turn real problems into ranked project suggestions.",
        "To implement Class Radar locking, supervisor review, semester archiving and a background decision engine that "
        "refines the closest matches.",
        "To test the system for correctness, security and performance.",
    ])

    w.p("Objectives 1 and 2 are covered in Chapters 2 and 3, objectives 3 to 5 in Chapter 4, and objective 6 in "
        "Chapters 4 and 5.")

    w.h2("1.4 Project Scope")
    w.p("Origin is built for a single college running BIT Project-IV. It runs on a college server or a lab machine, "
        "and students and supervisors use it from a browser. The scope was set early and kept.")
    w.label("Inclusions:")
    w.bullets([
        "Idea check: explainable scoring against the past-project corpus and this semester's locked ideas, with synonym "
        "folding, a claim detector and a result page that shows every component of the score.",
        "Class Radar: one locked idea per group per semester, automatic clash detection between groups, and supervisor "
        "review.",
        "Gap map and ideas: a domain by project-type map built from database views, and ranked suggestions built from a "
        "bank of real problems.",
        "Self-updating database: triggers for counters and the audit trail, a stored procedure that archives each "
        "semester into the corpus, and a background thread that refreshes suggestions.",
        "User roles: Student and Supervisor.",
    ])
    w.label("Exclusions:")
    w.bullets([
        "Checking finished reports or source code for copied text, which is the job of tools such as Turnitin.",
        "A mobile application, email notifications and multi-college use.",
        "Real-time chat between groups.",
    ])
    w.p("Origin assumes that students describe their idea honestly with meaningful tags. A student who writes a genuinely "
        "new description for an old idea can pass the check. We accept that limit, because at that point the student has "
        "done the thinking the check is meant to encourage.")
    w.h2("1.5 Report Organization")
    w.p("This report has six chapters. Chapter 1 introduces the problem, the objectives and the scope. Chapter 2 "
        "reviews the theory behind the system and the tools that already exist, and sets out the requirements and the "
        "feasibility study. Chapter 3 describes the development model, the timeline and the design: architecture, data "
        "flow, database, use cases and the main flowchart. Chapter 4 explains how each module was built and how the "
        "system was tested. Chapter 5 compares the finished system with each objective, and Chapter 6 closes with the "
        "problems we met, the limits of the system and the work that could follow. The references and the annexes, "
        "including the user manual, come after the last chapter.")


def ch2(w):
    w.h1("Chapter 2: Literature Overview")
    w.h2("2.1 Background / Theoretical Framework")
    w.p("Origin rests on a small number of well-known ideas, chosen because each one can be explained with a pencil and "
        "a piece of paper. This section describes them in the order the system uses them.")
    w.p("**Set similarity.** Origin treats an idea as sets: a set of tags and a set of meaningful words. Two sets are "
        "compared with the Jaccard index, the size of their intersection divided by the size of their union [3]. Two "
        "ideas with the tags {library, booking, notification} and {library, student, notification} share two tags out of "
        "four distinct ones, so their tag similarity is 0.5. The measure is easy to compute, easy to check by hand, and "
        "does not reward an idea for simply having more tags.")
    w.p("**Text normalisation.** Before words are compared they are lower-cased, split on anything that is not a letter "
        "or digit, and filtered through a stop-word list, a standard step in information retrieval [4]. Origin's list also "
        "removes words that describe nothing about the project, such as \"system\", \"management\", \"online\" and "
        "\"portal\". Simple plural folding turns \"libraries\" into \"library\". A synonym table then maps words like "
        "\"book\", \"lending\" and \"catalog\" to the canonical tag library, which is what makes renaming ineffective.")
    w.p("**Relational design.** The data model follows the relational model and is normalised to third normal form, so "
        "each fact is stored once [5]. Many-to-many links, such as projects and their tags, use junction tables with "
        "composite primary keys. Rules that must always hold, such as one locked idea per group per semester, are "
        "enforced by the database with unique keys and check constraints rather than by application code alone.")
    w.p("**Active databases.** A trigger is a stored program that runs automatically when a row is inserted, updated or "
        "deleted, and a stored procedure is a named program that runs a sequence of statements on request [6]. Origin "
        "uses both so that the database maintains its own counters and audit trail and can move a whole semester into "
        "the corpus inside one transaction.")
    w.p("**Server-side Java.** A servlet is a Java class that receives an HTTP request and produces a response inside a "
        "container, and Jakarta Server Pages turn templates into HTML on the server [7]. Apache Tomcat is the container "
        "used to run them [8]. Origin follows a three-layer pattern: servlets handle HTTP, services hold the logic, and "
        "data access objects talk to MySQL through JDBC.")
    w.p("**Password storage.** Passwords are never stored. Origin stores a PBKDF2 hash, which runs HMAC-SHA256 many "
        "thousands of times over the password and a random salt [9]. The salt makes identical passwords produce "
        "different hashes, and the repetition makes guessing slow, which is the approach current guidance recommends [10].")
    w.p("**Decision engines.** Rule scores miss ideas that mean the same thing in different words. Origin can ask an "
        "external decision engine, TypeSafe's Jev model, for a second opinion. Jev does not generate text. It receives "
        "a state and a set of typed questions and returns calibrated probabilities, for example the probability that two "
        "descriptions are the same project [11]. Because the answer is a number with a fixed meaning, Origin can blend it "
        "into its own score with a simple, documented formula.")

    w.h2("2.2 Study of Related Systems")
    w.p("We reviewed seven systems that students and supervisors use today, or could use, to judge whether a project "
        "is original. Table 2.1 summarises them.")
    w.table("2.1", "Study of Related Systems",
            ["S.N.", "Title / Year", "Technology", "Key Features", "Limitations"],
            [
                ["1", "Turnitin, Turnitin LLC, 1998 to present [1]", "Web service, text matching",
                 "Similarity report for submitted writing, large document database",
                 "Works on finished text; cannot judge an idea before it is written; paid licence"],
                ["2", "iThenticate, Turnitin LLC [12]", "Web service, text matching",
                 "Plagiarism screening for research manuscripts",
                 "Same after-the-fact model; built for publishers, not project proposals"],
                ["3", "MOSS, Schleimer et al., 2003 [2]", "Server, winnowing fingerprints",
                 "Finds similar source code across student submissions",
                 "Needs finished code; says nothing about the idea itself"],
                ["4", "JPlag, Prechelt et al., 2002 [13]", "Java, token string matching",
                 "Detects copied programs even after renaming variables",
                 "Code only; no view of past projects or current classmates"],
                ["5", "DSpace institutional repository [14]", "Java web application, keyword search",
                 "Stores and lists past theses and project reports",
                 "Passive archive; gives no similarity score and no gap view"],
                ["6", "Google Scholar [15]", "Web search engine",
                 "Finds published research on a topic",
                 "Covers research papers, not a college's own student projects"],
                ["7", "Manual supervisor review", "Human memory",
                 "Experienced supervisors recognise common repeats",
                 "Does not scale, misses renamed ideas and same-week clashes between groups"],
            ], [1.0, 3.2, 2.8, 4.0, 4.3], size=10)
    w.p("Note: The related systems listed above are cited in IEEE format in the References section.", first=False, size=10)
    w.p("The pattern is clear. Every automated tool in Table 2.1 compares finished work: written text or source code. "
        "None of them looks at an idea before development starts, none knows what other groups in the same class are "
        "doing this week, and none tells a student where the unexplored areas are. The repository and the search engine "
        "hold useful information, but a student has to search them by hand and judge the similarity alone.")

    w.h2("2.3 What's New in Our Project?")
    w.p("Origin fills the gaps found in Section 2.2:")
    w.bullets([
        "It works at proposal time, on the idea itself, before any report or code exists.",
        "It compares an idea with two pools at once: the college's own past projects and the ideas other groups locked "
        "this semester, so same-week clashes appear on the Class Radar for both groups.",
        "It explains every score as tags in common, words in common and project type, and it resists renaming through "
        "a synonym table and buzzword padding through a claim detector.",
        "It answers the next question as well: a gap map shows which domain and type combinations are untouched, and "
        "the Ideas page offers ranked suggestions built from real problems in Nepal.",
        "Its database keeps itself current. Triggers maintain counters and the audit trail, a stored procedure moves "
        "approved ideas into the corpus at the end of each semester, and a background thread refreshes the suggestions.",
        "A background decision engine refines the closest matches without slowing the page, and every answer is cached "
        "in the database so the same question is never asked twice.",
    ])

    w.h2("2.4 Functional and Non-Functional Requirements")
    w.p("The requirements below come from the problem statement and from the feedback we received at the proposal "
        "defense (see Table 2.2).")
    w.table("2.2", "System Requirements", ["Requirement Type", "Description", "Priority"], [
        ["Functional - User Auth", "Students register with a group code; students and supervisors sign in and out securely.", "High"],
        ["Functional - Idea check", "The system scores an idea against past projects and locked class ideas and shows each component of the score.", "High"],
        ["Functional - Anti-gaming", "Renamed ideas are caught through synonyms; unsupported buzzwords are flagged.", "High"],
        ["Functional - Class Radar", "Each group locks one idea per semester; clashes between groups are recorded and shown.", "High"],
        ["Functional - Gap map and ideas", "The system shows a domain by type map and ranked project suggestions from a problem bank.", "High"],
        ["Functional - Supervisor", "Supervisors approve or send back ideas, correct matches, close the semester and download a report.", "Medium"],
        ["Functional - History and export", "Every check is stored; students download their history as CSV.", "Medium"],
        ["Non-Functional - Performance", "A check completes and a page loads within 2 seconds under normal load.", "High"],
        ["Non-Functional - Security", "Passwords are hashed with a salt; forms carry CSRF tokens; roles are enforced on every request.", "High"],
        ["Non-Functional - Explainability", "Every score can be recomputed by hand from the tags, words and type shown on screen.", "High"],
        ["Non-Functional - Usability", "The interface works on desktop and phone screens without training.", "Medium"],
        ["Non-Functional - Reliability", "If the decision engine is unavailable, the system keeps working on the rule engine alone.", "Medium"],
    ], [4.2, 9.0, 2.0])

    w.h2("2.5 Feasibility Study")
    w.p("The feasibility study evaluates whether the proposed project can be successfully developed and deployed given "
        "the available resources, technology, and timeline.")
    w.p("Technically, every part of Origin uses topics from the fourth-semester syllabus: Java, JDBC, Servlets and JSP "
        "from Programming in Java, and SQL, triggers, procedures, normalisation and transactions from Database Management "
        "System. Operationally, students already write a title and a short description for every proposal, so Origin asks "
        "for nothing new. Economically, all tools are free. The one paid service, the decision engine, is optional and "
        "costs a fraction of a cent per question because answers are cached. Table 2.3 summarises the analysis.")
    w.table("2.3", "Feasibility Analysis", ["Feasibility Type", "Analysis", "Verdict"], [
        ["Technical", "Java 17, Apache Tomcat 10.1, MySQL 8 and JDBC are open-source, well documented and taught in the syllabus.", "Feasible"],
        ["Operational", "Students enter the same information they already write in a proposal; the pages need no training.", "Feasible"],
        ["Economic", "All tools are free. The optional decision engine charges per question, and cached answers are never paid for twice.", "Feasible"],
        ["Schedule", "The work fits into five increments over one semester, as shown in the Gantt chart (Table 3.1).", "Feasible"],
        ["Legal", "Only project metadata and student-entered text are stored; libraries and fonts are open-source or free licences.", "Feasible"],
    ], [3.2, 10.0, 2.0])
    w.p("Based on the above analysis, the project is determined to be fully feasible within the given academic context, "
        "available tools, and team capability.", size=11)


def ch3(w):
    w.h1("Chapter 3: System Design and Methodology")
    w.h2("3.1 Software Development Life Cycle (SDLC)")
    w.h3("3.1.1 SDLC Model Used")
    w.p("We followed the iterative and incremental model [16]. The system was split into five increments, and each one "
        "went through planning, design, building and testing before the next began (Figure 3.1). The first increment "
        "delivered the schema, the seed data and login. The second added the rule engine and the check flow, which made "
        "Origin usable on its own. The third added Class Radar and the gap map, the fourth the idea generator and the "
        "background decision engine, and the fifth the supervisor desk, the full test suite and this report.")
    w.p("The model suited a three-person team with a heavy exam load. After the second increment we always had a working "
        "system to demonstrate, so later increments could only improve the demo, never break it. It also absorbed the "
        "changes requested at the proposal defense: the call for more active use of the database became the triggers, "
        "the archive procedure and the self-refreshing suggestions of the third and fourth increments.")
    w.figure("3.1", DIAG / "fig-sdlc.png", "SDLC Model - Iterative and Incremental", 15.2)

    w.h2("3.2 Project Timeline - Gantt Chart")
    w.p("The Gantt chart in Table 3.1 shows the timeline from February to October 2026. Shaded cells mark the months in "
        "which each activity ran.")
    months = ["Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct"]
    plan = [
        ("Requirement study and literature review", {0, 1, 2}),
        ("Proposal writing and proposal defense", {3, 4, 5}),
        ("System and database design", {4, 5}),
        ("Increments 1 and 2: schema, seed data, rule engine", {5, 6}),
        ("Increment 3: Class Radar and gap map", {6}),
        ("Increment 4: idea generator and background engine", {6, 7}),
        ("Increment 5: supervisor desk", {7}),
        ("Testing and fixes", {7, 8}),
        ("Report and final presentation", {7, 8}),
    ]
    rows = []
    for name, active in plan:
        rows.append([name] + [("", "2F5D4A") if i in active else "" for i in range(len(months))])
    w.table("3.1", "Project Gantt Chart", ["Activity"] + months, rows, [5.8] + [1.05] * 9, size=9)
    w.p("Note: Shaded cells show the planned duration of each activity.", first=False, size=10)

    w.h2("3.3 System Architecture")
    w.p("Origin uses a layered client-server architecture (Figure 3.2). The browser receives HTML rendered from JSP pages. "
        "Every request first passes through a security filter, which checks the session, the role and the CSRF token and "
        "adds the security headers. Servlets act as controllers: they read the request, call a service and forward the "
        "result to a JSP view.")
    w.figure("3.2", DIAG / "fig-architecture.png", "System Architecture Diagram", 15.2, max_height_cm=17)
    w.p("The service layer holds the logic. CheckService validates and scores ideas, RadarService handles locking and "
        "clashes, IdeaService builds the suggestions, GapService prepares the map and AuthService signs people in. The "
        "similarity engine sits beside them as plain Java classes with no database access, which is what makes it easy "
        "to test. The data access layer uses JDBC with PreparedStatement for every query, transactions for every "
        "multi-step write, and CallableStatement for the stored procedures.")
    w.p("Two thread pools run beside the request threads. A pool of two worker threads sends background questions to the "
        "decision engine, and a timer thread refreshes the idea suggestions and retries unfinished checks every ten "
        "minutes. The decision engine is outside the system and optional: when it is not configured, the dashed part of "
        "the diagram simply does nothing.")

    w.h2("3.4 Data Flow Diagram (DFD)")
    w.h3("3.4.1 Level 0 - Context Diagram")
    w.p("At the highest level, Origin exchanges data with two people and one external system (Figure 3.3). Students send "
        "ideas, tags and lock requests and receive verdicts, matches, the gap map, suggestions and the radar. Supervisors "
        "send review decisions, corrections and the command to close a semester, and receive the locked ideas, clashes, "
        "statistics and the semester report. The decision engine receives a state with typed questions and returns "
        "probabilities.")
    w.figure("3.3", DIAG / "fig-dfd0.png", "Data Flow Diagram - Level 0 (Context Diagram)", 12.5)
    w.h3("3.4.2 Level 1 - Detailed DFD")
    w.p("Figure 3.4 breaks the system into six processes and seven data stores. Authentication (1.0) reads the users. "
        "Checking an idea (2.0) reads the corpus and the locked proposals and writes the check with its matches. Locking "
        "(3.0) writes the proposal and its clashes, and the audit log is written by triggers. Building the gap map and "
        "ideas (4.0) reads the corpus and proposals and writes the suggestions. Review and archiving (5.0) updates "
        "proposals and, at the end of the semester, writes approved ideas into the corpus. Background refinement (6.0) "
        "reads and updates checks and exchanges questions and answers with the decision engine through the cache.")
    return_to_landscape_figure(w, "3.4", DIAG / "fig-dfd1.png", "Data Flow Diagram - Level 1")

    w.h2("3.5 Entity Relationship (ER) Diagram")
    w.p("The database has 19 tables in third normal form (Figure 3.5). The historical corpus lives in corpus_projects, "
        "linked to tags through corpus_project_tags. Every check a student runs is stored in idea_checks with its tags in "
        "idea_check_tags and its scored matches in check_matches. A match points either to a corpus project or to a "
        "proposal, never both, which a check constraint enforces. When a group locks a check it becomes a row in "
        "proposals, whose composite unique key on group_code and semester_id enforces one idea per group per semester. "
        "Clashes between proposals are rows in radar_collisions.")
    w.p("The ideas side has its own tables: problem_bank holds real problems with their tags in problem_tags, and "
        "idea_suggestions holds one row per problem and suitable project type. Supervisor corrections live in "
        "match_overrides, decision-engine answers in engine_cache, and the audit_log is written only by triggers. "
        "Domains, project types and semesters are small reference tables.")
    return_to_landscape_figure(w, "3.5", DIAG / "fig-er.png", "Entity Relationship (ER) Diagram")

    w.h2("3.6 Use Case Diagram")
    w.p("As shown in Figure 3.6, the system supports thirteen primary use cases across two actor roles, plus two use "
        "cases started by the system itself. A student registers, checks an idea, reads the result, explores the gap "
        "map, browses ideas, locks an idea for the group, views the Class Radar and downloads the check history. A "
        "supervisor can do everything a student can and also reviews locked ideas, corrects matches, closes the "
        "semester, downloads the semester report and reads the statistics and audit trail. The timer and the decision "
        "engine drive background refinement and the refresh of suggestions.")
    w.figure("3.6", DIAG / "fig-usecase.png", "Use Case Diagram", 15.2, max_height_cm=17)

    w.h2("3.7 System Flowchart")
    w.p("Figure 3.7 follows one idea from sign-in to lock. Invalid input loops back with field errors. A valid idea is "
        "normalised, scored against both pools, adjusted by any remembered supervisor correction, banded and saved in one "
        "transaction. The result page appears at once; if the decision engine is configured, a background thread refines "
        "the verdict a few seconds later. Locking is refused for a duplicate or for a group that has already locked an "
        "idea; otherwise the proposal is saved, compared with every other group's idea, and any clash is recorded.")
    w.figure("3.7", DIAG / "fig-flowchart.png", "System Flowchart", 15.2, max_height_cm=19.5)


def return_to_landscape_figure(w, number, path, title):
    """Puts one wide figure on its own landscape page, then returns to portrait."""
    doc = w.doc
    body_sect = doc.sections[-1]
    land = doc.add_section(WD_SECTION.NEW_PAGE)
    strip_restart(land)
    land.orientation = WD_ORIENT.LANDSCAPE
    land.page_width, land.page_height = body_sect.page_height, body_sect.page_width
    w.figure(number, path, title, 24.0, max_height_cm=13.6)
    port = doc.add_section(WD_SECTION.NEW_PAGE)
    strip_restart(port)
    port.orientation = WD_ORIENT.PORTRAIT
    port.page_width, port.page_height = land.page_height, land.page_width


def strip_restart(section):
    pg = section._sectPr.find(qn("w:pgNumType"))
    if pg is not None and pg.get(qn("w:start")) is not None:
        del pg.attrib[qn("w:start")]


def ch4(w):
    w.h1("Chapter 4: Implementation")
    w.h2("4.1 Software and Hardware Requirements")
    w.p("Table 4.1 lists the hardware and software used to develop and run Origin, with versions.")
    w.table("4.1", "Hardware and Software Requirements", ["Component", "Specification / Tool", "Purpose"], [
        ["Processor", "Intel Core i3 or equivalent (1 GHz+)", "Compiling and running the server"],
        ["RAM", "Minimum 4 GB (8 GB recommended)", "Tomcat, MySQL and the IDE together"],
        ["Storage", "Minimum 2 GB free disk space", "Source, dependencies and database files"],
        ["Operating System", "Windows 10 / 11 or Ubuntu 20.04 or later", "Development and deployment"],
        ["Browser", "Google Chrome / Mozilla Firefox (latest)", "Using and testing the interface"],
        ["Code Editor / IDE", "IntelliJ IDEA / Visual Studio Code", "Development environment"],
        ["Language", "Java 17 (JDK 17 or newer)", "All server-side code"],
        ["Web container", "Apache Tomcat 10.1 (Servlet 6.0, JSP 3.1)", "Runs servlets and JSP pages"],
        ["View layer", "JSP with JSTL 3.0, hand-written CSS and JavaScript", "Server-rendered pages"],
        ["Database", "MySQL 8.4 with Connector/J 8.4", "Data, triggers, views and procedures"],
        ["JSON", "Jakarta JSON Processing (Eclipse Parsson 1.1)", "Decision-engine requests and replies"],
        ["Build tool", "Apache Maven 3.9", "Dependencies, compiling, tests and WAR packaging"],
        ["Testing", "JUnit 5, Playwright (screenshots), curl", "Automated and manual tests"],
        ["Version Control", "Git & GitHub", "Source code management"],
    ], [3.4, 6.5, 5.3])
    w.h3("4.1.1 Technology Stack and Language Used")
    w.p("Every choice below comes from the fourth-semester syllabus unless stated otherwise.")
    w.bullets([
        "**Back-end: Java Servlets on Apache Tomcat 10.1.** Servlets are the syllabus way to write web applications in "
        "Java, and Tomcat is the container named in it. Seventeen servlets act as controllers, one filter handles "
        "security and a context listener starts the application [7], [8], [17].",
        "**Front-end: JSP with JSTL.** Pages are rendered on the server, so they work without JavaScript. A small script "
        "adds the live corner preview, the tag picker and the status polling on top.",
        "**Database: MySQL 8 through JDBC.** A relational database suits data made of projects, tags and the links "
        "between them. JDBC with PreparedStatement protects every query from SQL injection, and transactions keep "
        "multi-step writes all-or-nothing [5], [6], [18].",
        "**Decision engine: TypeSafe Jev over HTTPS (optional).** Called with java.net.http, which is part of the JDK. "
        "It is the one component outside the syllabus, and the system works without it [11].",
        "**Version control: Git & GitHub** for collaborative development and code versioning.",
        "**Deployment: localhost or any Tomcat 10.1 server.** A launcher starts an embedded Tomcat with one command for "
        "development and the demonstration; the same code builds a WAR file for a standalone Tomcat.",
    ])

    w.h2("4.2 Module-wise Implementation")
    w.p("Origin has seven modules. Each description below points to the classes that implement it and to the "
        "screenshots taken from the running system.")

    w.h3("4.2.1 Authentication and Security Module")
    w.p("AuthService registers students and signs people in. A password is hashed with PBKDF2 using HMAC-SHA256, "
        "120,000 iterations and a random 16-byte salt, and the hash is compared in constant time so that the response "
        "time does not leak how much of it matched [9], [10]. A failed login for an unknown email costs the same hashing "
        "time as a wrong password, so timing cannot reveal which emails have accounts. After five failed attempts from "
        "one address in five minutes, further attempts receive HTTP 429.")
    w.p("SecurityFilter runs before every request. It sends anonymous users to the sign-in page, returns 403 when a "
        "student opens a supervisor page, rejects any POST without the session's CSRF token, and sets the "
        "Content-Security-Policy, X-Frame-Options and X-Content-Type-Options headers. Session cookies are HttpOnly with "
        "SameSite=Lax, and a new session id is issued at login. Figure 4.1 shows the sign-in page.")
    w.figure("4.1", IMG / "f41-login.png", "Screenshot - Sign-in Page", 15.0)

    w.h3("4.2.2 Similarity Engine Module")
    w.p("The engine is plain Java with no database access. An idea becomes a Candidate: a set of canonical tags, a set "
        "of meaningful keywords and a project type. Three scorers implement one Scorer interface, and SimilarityEngine "
        "loops over a List<Scorer> without knowing which concrete class it is calling. This is where inheritance and "
        "polymorphism do real work in the project.")
    w.code([
        "public interface Scorer {",
        "    String name();",
        "    double weight();         // all weights add up to 1.0",
        "    double score(Candidate idea, Candidate other); // 0 to 1",
        "}",
        "",
        "public class TagScorer implements Scorer {",
        "    public double weight() { return 0.45; }",
        "    public double score(Candidate idea, Candidate other) {",
        "        return Scorer.jaccard(idea.tags(), other.tags());",
        "    }",
        "}",
    ])
    w.p("The weights and bands are fixed and published on the check page, as Table 4.2 shows. Tags weigh most because "
        "they describe what a project does, and function does not change when the title does. The same thresholds are "
        "written once in Java and once in the SQL function fn_band, and a test checks that they agree.")
    w.table("4.2", "Scoring Signals, Weights and Bands", ["Item", "Rule", "Value"], [
        ["Tags in common", "Jaccard index of canonical tag sets", "45% of the score"],
        ["Words in common", "Jaccard index of title and description keywords", "35% of the score"],
        ["Same type", "1 if both are the same project type, otherwise 0", "20% of the score"],
        ["Meaning (background)", "Decision-engine probability that both are the same project", "Blended 60% rules, 40% meaning"],
        ["Looks original", "Final score below 0.35", "Band ORIGINAL"],
        ["Overlaps", "Final score from 0.35 to 0.59", "Band SIMILAR"],
        ["Already done", "Final score of 0.60 or more", "Band DUPLICATE"],
        ["Not enough data", "Fewer than 15 projects in the corpus", "Band LOW_CONFIDENCE"],
    ], [4.0, 7.0, 4.2])
    w.p("Two guards sit beside the scorers. The Vocabulary class folds 87 synonyms into 104 canonical tags, so "
        "\"books, lending, reservation\" becomes {library, booking}. The ClaimDetector watches six kinds of claim, "
        "including AI, prediction, IoT and blockchain. If one appears in the title or tags but the description names no "
        "data, device or method to back it up, the result page says so.")

    w.h3("4.2.3 Idea Check and Result Module")
    w.p("The check form asks for a title, a domain, a project type, a description, an optional problem statement and "
        "tags. While the student picks a domain and type, a panel beside the form shows how crowded that corner of the "
        "map already is (Figure 4.2).")
    w.figure("4.2", IMG / "f42-check.png", "Screenshot - Check Form with Live Corner Preview", 15.0, max_height_cm=18)
    w.p("CheckService validates the input, builds the Candidate, ranks the five closest past projects and the three "
        "closest locked class ideas, applies any remembered supervisor correction, and saves the check, its tags and its "
        "matches in one transaction. Figure 4.3 shows the rename test from Chapter 1. \"Book Depot Portal\", described "
        "as students reserving books and getting reminders, scores 74% and lands in the Already done band. Its closest "
        "past project is the Online Library Book Lending Portal from 2021: 50% of tags and 54% of words in common, the "
        "same type, and a 93% probability from the decision engine that the two are the same project. Group G01's "
        "Library Study Seat Booking shares half the tags as well, but the engine gives it 5%, so it stays at 31%: "
        "reserving a seat is not reserving a book. Every component of every score is on the page.")
    w.figure("4.3", IMG / "f43-result-dup.png", "Screenshot - Renamed Library Idea Caught", 15.0, max_height_cm=18)
    w.p("Figure 4.4 shows the opposite case. A health-post medicine stock alert scores 25%, falls in the Looks original "
        "band, and the page offers to lock it for group G09.")
    w.figure("4.4", IMG / "f44-result-orig.png", "Screenshot - Original Idea Ready to Lock", 15.0)

    w.h3("4.2.4 Class Radar Module")
    w.p("RadarService locks an idea for the student's group. It refuses when the account has no group, when the verdict "
        "is Already done, or when the group has already locked an idea. The last rule is enforced twice: once in Java "
        "for a friendly message, and once by the unique key on the proposals table, which stops two teammates who click "
        "at the same moment. A lock always uses the final verdict: if the background comparison has not finished, "
        "the lock runs it first. After the insert, the new idea is scored against every other locked idea, and every pair "
        "at 35% or above is written to radar_collisions in the same transaction. A trigger copies each clash into the "
        "audit trail. In the seed data, group G06's Verified Blood Request Line and group G07's Rapid Blood Request Relay "
        "clash at 41%, and both rows of Figure 4.5 show it.")
    w.figure("4.5", IMG / "f47-radar.png", "Screenshot - Class Radar with a Clash between G06 and G07", 15.0)

    w.h3("4.2.5 Gap Map and Idea Generator Module")
    w.p("The gap map is the v_gap_matrix view: a cross join of 12 domains and 6 project types with a count of past "
        "projects, locked ideas and open suggestions for each of the 72 cells. With the seed data, 40 cells have no past "
        "project at all. Selecting a cell lists what was built there, what is locked there this semester, and which open "
        "ideas fit it (Figure 4.6).")
    w.figure("4.6", IMG / "f45-gap-cell.png", "Screenshot - Gap Map Cell Detail", 15.0)
    w.p("IdeaService pairs each of the 42 problems in the problem bank with its suitable project types, which gives 84 "
        "suggestions. Each one is scored on four things: how different it is from the closest existing project, how "
        "empty its cell is, how much a three-person team can build in a semester, and whether it helps a clear group of "
        "people. The strength is 0.35 x novelty + 0.25 x opportunity + 0.25 x feasibility + 0.15 x impact. A suggestion "
        "that a locked idea already matches at 45% or more is marked as taken by that group. Figure 4.7 shows the Ideas "
        "page, where each problem appears once with its stronger type.")
    w.figure("4.7", IMG / "f46-ideas.png", "Screenshot - Ranked Project Ideas", 15.0, max_height_cm=18)

    w.h3("4.2.6 Background Decision Engine Module")
    w.p("When a key is configured, CheckService hands the new check to a worker thread and returns immediately. The "
        "worker sends one request to the decision engine with the idea and all eight matches on the page as the state, and one "
        "yes/no question per match, such as \"Is idea essentially the same project as candidates[0]?\". A second "
        "question asks whether the idea claims an advanced technique, such as AI or prediction, that its description "
        "gives no concrete basis for [11]. The probabilities "
        "come back in 70 to 500 milliseconds; each match's final score becomes 60% rule score plus 40% probability; and "
        "the verdict is updated. The result page polls a small JSON endpoint and reloads once the answer arrives.")
    w.p("For suggestions, IdeaService asks whether the team can build the idea in one semester, whether it helps an "
        "identifiable group of people in Nepal, and where it sits on a five-level novelty scale and a four-level "
        "difficulty scale. Every request body is hashed with SHA-256 and its reply is stored in engine_cache, so a "
        "repeated question is answered from MySQL without a network call. If the engine fails, the check is marked "
        "FAILED and the maintenance thread retries it later; the page never waits for it.")

    w.h3("4.2.7 Supervisor Desk and Self-Updating Database")
    w.p("The supervisor desk (Figure 4.8) lists the locked ideas waiting for review with their clashes, the reviewed "
        "ones, the share of past projects per domain from the v_domain_saturation view, the tags students check most, "
        "and the audit trail. On any result page, a supervisor can mark a match as the same project or a different one. "
        "The correction is stored against the idea's fingerprint, a SHA-256 hash of its sorted tags and keywords, so "
        "the same idea checked again later receives the corrected verdict without asking anyone.")
    w.figure("4.8", IMG / "f48-admin.png", "Screenshot - Supervisor Desk", 15.0, max_height_cm=18)
    w.p("The review comments before the proposal defense asked for more active use of the database. Table 4.3 lists the "
        "database objects that now do work on their own.")
    w.table("4.3", "Database Objects that Keep Origin Current", ["Object", "Type", "What it does"], [
        ["trg_cpt_count, trg_ict_count", "Triggers", "Keep per-tag counts for the corpus and for checks; copy a student problem's tags into the problem bank"],
        ["trg_check_problem", "Trigger", "Copies a student's problem statement into the problem bank"],
        ["trg_proposal_locked, trg_proposal_reviewed, trg_collision_audit", "Triggers", "Write every lock, review and clash to the audit log"],
        ["v_gap_matrix, v_domain_saturation, v_class_radar, v_check_history", "Views", "Supply the gap map, statistics, radar and history pages"],
        ["fn_band", "Stored function", "Turns a score into a band with the same thresholds as the Java code"],
        ["sp_archive_semester", "Stored procedure", "In one transaction: copies approved ideas and tags into the corpus, closes the semester, opens the next"],
        ["sp_semester_report", "Stored procedure", "Returns one row per group for the supervisor's CSV report"],
    ], [5.2, 2.8, 7.2])

    w.h2("4.3 Testing")
    w.h3("4.3.1 Testing Strategy")
    w.p("We tested at three levels. Unit tests with JUnit 5 cover the scorers, the vocabulary, the claim detector, the "
        "password hasher, the CSV writer, the SQL script splitter and the decision-engine client with a simulated "
        "transport. Integration tests run the real services against a separate MySQL database named origin_test, which "
        "is dropped and rebuilt on every run, so they exercise the triggers, views, constraints and stored procedures. "
        "System tests drove the running application: HTTP checks with curl for security rules, and a Playwright script "
        "that signs in, submits ideas and captures every page on desktop and phone screens.")
    w.p("The accuracy of the engine was tested against human judgement. Before running the engine, the team labelled 15 "
        "pairs of ideas as Looks original, Overlaps or Already done. The test fails if the engine agrees on fewer than 12 "
        "of the 15.")
    w.h3("4.3.2 Test Cases")
    w.p("The following test cases (Table 4.4) document the testing carried out on the system. Each test case verifies a "
        "specific functionality against expected behavior.")
    tc = [
        ["TC-01", "Valid user login", "sandesh@origin.edu, correct password", "Redirect to Check an idea", "HTTP 302 to /app/check", "Pass", "System"],
        ["TC-02", "Invalid login credentials", "Correct email, wrong password", "Error message, no session", "\"That email and password do not match an account.\"", "Pass", "System"],
        ["TC-03", "Repeated failed logins", "Six wrong passwords in a row", "Sixth attempt refused", "HTTP 429 on the sixth attempt", "Pass", "Security"],
        ["TC-04", "Registration with invalid data", "Name \"A\", bad email, short password, group \"7\"", "Error on each field", "4 field errors returned", "Pass", "Unit"],
        ["TC-05", "Registration with duplicate email", "sandesh@origin.edu", "Duplicate email error shown", "\"An account with this email already exists.\"", "Pass", "Unit"],
        ["TC-06", "Empty or short fields in the check form", "Title \"Hi\", 10-character description, one tag", "Errors on title, description and tags", "3 field errors, nothing saved", "Pass", "Integration"],
        ["TC-07", "Check - renamed library idea", "\"Book Depot Portal\" with books, lending, reservation", "Already done; closest match is a library project", "DUPLICATE; top match Online Library Book Lending Portal", "Pass", "Integration"],
        ["TC-08", "Lock a duplicate idea", "The check from TC-07", "Lock refused", "\"This idea is marked as already done...\"", "Pass", "Integration"],
        ["TC-09", "Check and lock an original idea", "Livestock Vaccination Reminder", "Looks original; proposal created", "ORIGINAL; proposal saved for G09", "Pass", "Integration"],
        ["TC-10", "Second lock by the same group", "Any check by a G09 member", "Lock refused", "\"Group G09 has already locked an idea this semester.\"", "Pass", "Integration"],
        ["TC-11", "Buzzword padding", "\"AI Powered Hostel Prediction System\"", "Claims flagged with reasons", "Claim flag set, 2 reasons shown", "Pass", "Integration"],
        ["TC-12", "Background refinement", "Simulated engine answering 0.95", "Status DONE; final = 0.6 rule + 0.4 x 0.95", "Status DONE; final score matched to 0.001", "Pass", "Integration"],
        ["TC-13", "Supervisor correction remembered", "Mark a match \"Different\", check the same idea again", "Correction applied without asking again", "Meaning score 0.0 on the second check", "Pass", "Integration"],
        ["TC-14", "Triggers keep counters and audit", "Seed data for 8 groups", "Tag counts equal link rows; 8 LOCK rows", "Counts equal; 8 LOCK and 1 COLLISION rows", "Pass", "Integration"],
        ["TC-15", "Close the semester", "Approve G02, call sp_archive_semester", "1 project moved; radar empty; new semester active", "1 moved; 0 proposals; 1 active semester", "Pass", "Integration"],
        ["TC-16", "Student opens the supervisor desk", "Student session, GET /admin", "Access refused", "HTTP 403 \"Supervisors only\"", "Pass", "Security"],
        ["TC-17", "Form posted without CSRF token", "POST /login without _csrf", "Request refused", "HTTP 403 \"Form expired\"", "Pass", "Security"],
        ["TC-18", "Another student's result", "GET /app/result?id=1 as another student", "Not shown", "HTTP 404", "Pass", "Security"],
        ["TC-19", "Export check history", "GET /app/history.csv", "CSV file download", "text/csv attachment with a header row", "Pass", "System"],
        ["TC-20", "Agreement with human labels", "15 labelled idea pairs", "At least 12 of 15 agree", "14 of 15 agree", "Pass", "Unit"],
        ["TC-21", "Lock before the background check finishes", "Pending check; engine says same project (0.99)", "Comparison runs first, then the lock is refused", "Status DONE; \"This idea is marked as already done...\"", "Pass", "Integration"],
    ]
    w.table("4.4", "Test Cases", ["TC ID", "Test Description", "Input Data", "Expected Output", "Actual Output", "Status", "Type"],
            tc, [1.3, 2.6, 2.8, 2.9, 3.1, 1.2, 1.7], size=9)
    w.p("Note: All test cases were run on the final build. No test case failed in the final run; the failure found during "
        "development is described in Section 4.3.3.", first=False, size=10)
    w.h3("4.3.3 Test Results Summary")
    w.bullets([
        "Total automated tests executed: 35 (21 unit, 14 integration), plus 13 HTTP checks with curl and 26 page "
        "captures with Playwright (13 desktop, 13 phone).",
        "Tests passed: 35 of 35, and every HTTP check returned the expected status.",
        "Failed during development: 1. The rename test first scored 0.5 instead of at least 0.66 for tags, because plural "
        "tags such as \"books\" were not folded to their synonym. The fix folds a plural tag only when its singular is a "
        "known tag or synonym, so real tags such as \"analytics\" stay intact. Agreement with human labels rose from 13 "
        "to 14 of 15.",
        "Tools used: JUnit 5, Maven Surefire, a separate MySQL test database, curl and Playwright.",
    ])


def ch5(w):
    w.h1("Chapter 5: Final Outcome")
    w.h2("5.1 System Output and Analysis")
    w.p("The final system runs on Apache Tomcat with MySQL and serves eleven pages to two roles. The landing page opens "
        "on the live gap map, with the headline built from the database: 71 projects built and 37 of 72 plots still "
        "open (Figure 5.1). Each count changes as soon as a group locks an idea or a semester is archived.")
    w.figure("5.1", IMG / "f51-home.png", "Screenshot - Landing Page with the Live Gap Map", 15.0)
    w.p("Further down, the landing page runs the rename test through the live engine every time it loads (Figure 5.2). "
        "\"Book Depot Portal\" is matched to the Online Library Book Lending Portal with 67% of tags and 70% of words in "
        "common and the same type, for 74% overall. The number is computed, not typed, so it stays true if the corpus "
        "changes.")
    w.figure("5.2", IMG / "f52-rename.png", "Screenshot - Rename Example Computed by the Live Engine", 15.0)

    w.h2("5.2 Analysis of Output Against Objectives")
    outcomes = [
        ("Objective 1: To analyse how BIT project ideas repeat and to study existing originality and plagiarism tools.",
         "Seven related systems were studied (Table 2.1); all of them check finished work. The sample corpus of 71 "
         "typical projects shows the pattern: 40 are web applications and education alone holds 15, while 40 of the 72 "
         "domain and type combinations have no project at all."),
        ("Objective 2: To design a layered architecture and a normalised schema that keeps itself current.",
         "The system uses three layers (Figure 3.2) and 19 tables in third normal form (Figure 3.5). Six triggers, four "
         "views, one stored function and two stored procedures do work on their own (Table 4.3). TC-14 and TC-15 prove "
         "the triggers and the archive procedure against a real database."),
        ("Objective 3: To develop an explainable similarity engine that resists renaming and buzzword padding.",
         "Every score on the result page is split into tags, words and type (Figure 4.3). The renamed library idea is "
         "caught at 74% (TC-07) and padding is flagged (TC-11). The engine agrees with the team's human labels on 14 of "
         "15 pairs (TC-20)."),
        ("Objective 4: To develop a gap map and an idea generator.",
         "The gap map shows all 72 cells with drill-down (Figure 4.6). The generator turns 42 problems into 84 scored "
         "suggestions and marks the 5 that locked groups have already taken (Figure 4.7)."),
        ("Objective 5: To implement Class Radar, supervisor review, semester archiving and background refinement.",
         "Locking, clash detection and review work end to end (Figures 4.5 and 4.8; TC-09, TC-10). The G06 and G07 clash "
         "is detected at 41%. Background refinement blends the engine's answer exactly as documented (TC-12), and "
         "supervisor corrections are remembered (TC-13)."),
        ("Objective 6: To test the system for correctness, security and performance.",
         "All 35 automated tests pass, the security rules hold over real HTTP (TC-16 to TC-18), and the timings in "
         "Section 5.4 are well inside the two-second requirement."),
    ]
    for obj, out in outcomes:
        w.label(obj)
        w.p(f"**Outcome:** {out}")

    w.h2("5.3 Final System Screenshots")
    w.p("Figure 5.3 shows a student's check history, which can be downloaded as CSV for a proposal, and Figure 5.4 shows "
        "the same pages on a phone screen. The gap map scrolls sideways inside its frame, and no page scrolls sideways as "
        "a whole at a 390-pixel width.")
    w.figure("5.3", IMG / "f53-history.png", "Screenshot - Check History with CSV Export", 15.0)
    w.figure("5.4", IMG / "f54-mobile.png", "Screenshot - Landing, Result and Ideas Pages on a Phone", 14.0, max_height_cm=13)

    w.h2("5.4 Performance and Usability Observations")
    w.p("We timed the running system on a development laptop with MySQL on the same machine, averaging five requests per "
        "page. Table 5.1 shows the results.")
    w.table("5.1", "Measured Response Times", ["Request", "Average time"], [
        ["Landing page with gap map and live rename example", "105 ms"],
        ["Gap map page", "71 ms"],
        ["Ideas page (first 15 of 41)", "72 ms"],
        ["Class Radar", "51 ms"],
        ["Check form", "70 ms"],
        ["Submit a check (validate, score 79 candidates, save in one transaction)", "90 to 104 ms"],
    ], [11.0, 4.2])
    w.p("Every request finished in under 110 milliseconds, far inside the two-second requirement. A decision-engine "
        "call took about 1.2 seconds in our live tests, but it runs on a background thread, so no page waits for it. The corpus is small, "
        "but the cost grows linearly: scoring one idea against a thousand projects is about a thousand Jaccard "
        "comparisons of small sets, which takes milliseconds in Java.")
    w.p("For usability, we checked the pages at desktop and phone widths with automated screenshots and fixed every "
        "problem we found before release. Examples include navigation labels wrapping onto two lines, uneven panels in "
        "the cell detail, and a display font whose old-style figure 1 looked like the letter I; the display face now "
        "uses lining figures. Every page works without JavaScript: the script adds the live preview and polling but no "
        "content depends on it.")


def ch6(w):
    w.h1("Chapter 6: Conclusion, Limitations & Future Recommendations")
    w.h2("6.1 Conclusion")
    w.p("This project set out to give BIT students a way to check a project idea before committing a semester to it. "
        "Origin does that with Java Servlets, JSP and JDBC on Apache Tomcat and a MySQL database. It compares an idea with "
        "the college's past projects and with the ideas other groups have locked, explains every score, catches renamed "
        "ideas and unsupported buzzwords, and shows each group's clashes on the Class Radar.")
    w.p("It also answers the question that follows a failed check. The gap map shows which domains and project types "
        "nobody has touched, and the Ideas page ranks real problems from Nepal by how new, how open, how buildable and "
        "how useful they are.")
    w.p("The database does part of the work. Triggers maintain counters and the audit trail, a stored procedure moves "
        "each semester's approved ideas into the corpus in one transaction, and a background thread keeps the "
        "suggestions current. All six specific objectives were met, as Section 5.2 shows, with all 35 automated tests "
        "passing and 14 of 15 agreements with human labels. The result is a tool that helps students and supervisors "
        "have a better conversation about ideas, earlier in the semester.")
    w.h2("6.2 Problems Faced and Limitations")
    w.label("Challenges Encountered:")
    w.bullets([
        "Seed data that the engine rejected. Two of our own seeded group ideas were close copies of corpus projects, and "
        "the engine refused to lock them on the first run. We rewrote those seed ideas; the engine was right.",
        "Embedded Tomcat under Maven. Tomcat could not load its own classes through Maven's exec plugin. We launch it as a "
        "plain Java process with the full classpath instead, which is also simpler for the demonstration.",
        "A display font with old-style figures. The chosen display face drew the digit 1 like the letter I, which "
        "confused the map. We combined two font files so that letters and digits come from different faces.",
        "Plural tags. \"books\" was not folded to library at first (Section 4.3.3). Folding plurals only for known words "
        "fixed it without damaging tags such as \"analytics\".",
        "A decision-engine question that could not decide. Our first question, \"does the description explain every "
        "advanced technique it mentions?\", returned 0.54 for an air-quality station that names its PM2.5 sensor and 0.58 "
        "for an idea that only says \"AI\". Following the vendor's advice to ask one narrow thing, we asked instead whether "
        "the idea claims a technique with no concrete basis: 0.03 against 0.85. The live test caught a false flag on a "
        "seeded group before release.",
        "A verdict that changed after a lock. In one seeded run a group locked an idea at 51%, and the background "
        "comparison raised it to 64% a second later. Locking now finishes the comparison first (TC-21).",
    ])
    w.label("Current Limitations:")
    w.bullets([
        "The corpus and problem bank are seed data prepared by the team. The college's real archive of past projects has "
        "to be entered before Origin can judge real proposals.",
        "Scores depend on sensible tags. A student who tags carelessly weakens the comparison.",
        "The live decision engine is an external paid service in early access. The integration was tested with a "
        "simulated engine that follows the documented API; the live service must be tested once the key is configured.",
        "Origin is designed for one college and one programme at a time.",
        "It compares ideas, not writing or code, and does not replace tools such as Turnitin for finished reports.",
    ])
    w.h2("6.3 Future Recommendations")
    w.bullets([
        "Import the college's real archive of past project titles and abstracts from a spreadsheet through a supervisor "
        "upload page.",
        "Let supervisors add and edit problems in the problem bank from the supervisor desk, including problems sent by "
        "local organisations.",
        "Send an email to both groups when a clash is detected on the Class Radar.",
        "Measure agreement with human labels on a larger set of pairs each semester and tune the weights from the results.",
        "Support several programmes and colleges, each with its own corpus and radar.",
        "Add a view of how each domain's share of projects changes over the years.",
    ])


REFS = [
    'Turnitin, LLC, "Turnitin Similarity," Turnitin. [Online]. Available: https://www.turnitin.com/products/similarity. [Accessed: 26 Sep. 2026].',
    'S. Schleimer, D. S. Wilkerson, and A. Aiken, "Winnowing: Local algorithms for document fingerprinting," in Proc. ACM SIGMOD Int. Conf. Management of Data, San Diego, CA, USA, 2003, pp. 76-85.',
    'P. Jaccard, "The distribution of the flora in the alpine zone," New Phytologist, vol. 11, no. 2, pp. 37-50, Feb. 1912.',
    'C. D. Manning, P. Raghavan, and H. Schütze, Introduction to Information Retrieval. Cambridge, U.K.: Cambridge University Press, 2008.',
    'A. Silberschatz, H. F. Korth, and S. Sudarshan, Database System Concepts, 7th ed. New York, NY, USA: McGraw-Hill, 2019.',
    'Oracle Corporation, "MySQL 8.4 Reference Manual: Stored Objects," MySQL. [Online]. Available: https://dev.mysql.com/doc/refman/8.4/en/stored-objects.html. [Accessed: 26 Sep. 2026].',
    'Eclipse Foundation, "Jakarta Servlet Specification, Version 6.0," Jakarta EE. [Online]. Available: https://jakarta.ee/specifications/servlet/6.0/. [Accessed: 26 Sep. 2026].',
    'The Apache Software Foundation, "Apache Tomcat 10.1 Documentation," Version 10.1. [Online]. Available: https://tomcat.apache.org/tomcat-10.1-doc/. [Accessed: 26 Sep. 2026].',
    'K. Moriarty, B. Kaliski, and A. Rusch, "PKCS #5: Password-based cryptography specification version 2.1," Internet Engineering Task Force, RFC 8018, Jan. 2017.',
    'OWASP Foundation, "Password Storage Cheat Sheet," OWASP Cheat Sheet Series. [Online]. Available: https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html. [Accessed: 26 Sep. 2026].',
    'D. Almeida, "Introducing System One Models and Jev," TypeSafe AI, Sep. 14, 2026. [Online]. Available: https://typesafe.ai/blog/introducing-system-one-models-and-jev. [Accessed: 26 Sep. 2026].',
    'Turnitin, LLC, "iThenticate," iThenticate. [Online]. Available: https://www.ithenticate.com. [Accessed: 26 Sep. 2026].',
    'L. Prechelt, G. Malpohl, and M. Philippsen, "Finding plagiarisms among a set of programs with JPlag," Journal of Universal Computer Science, vol. 8, no. 11, pp. 1016-1038, Nov. 2002.',
    'LYRASIS, "DSpace," DSpace. [Online]. Available: https://dspace.lyrasis.org. [Accessed: 26 Sep. 2026].',
    'Google, "Google Scholar," Google. [Online]. Available: https://scholar.google.com. [Accessed: 26 Sep. 2026].',
    'I. Sommerville, Software Engineering, 10th ed. Harlow, U.K.: Pearson, 2016.',
    'H. Schildt, Java: The Complete Reference, 12th ed. New York, NY, USA: McGraw-Hill, 2021.',
    'R. Elmasri and S. B. Navathe, Fundamentals of Database Systems, 7th ed. Hoboken, NJ, USA: Pearson, 2016.',
]


def references(w):
    head = w.doc.add_paragraph(style="Heading 1")
    set_font(head.add_run("REFERENCES"), 16, bold=True)
    head.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.CENTER
    head.paragraph_format.page_break_before = True
    head.paragraph_format.space_after = Pt(18)
    for i, r in enumerate(REFS, 1):
        para = w.doc.add_paragraph()
        # Titles of books and journals in italics, per the IEEE guideline
        r = r.replace("Introduction to Information Retrieval.", "~~Introduction to Information Retrieval~~.") \
             .replace("Database System Concepts,", "~~Database System Concepts~~,") \
             .replace("New Phytologist,", "~~New Phytologist~~,") \
             .replace("Journal of Universal Computer Science,", "~~Journal of Universal Computer Science~~,") \
             .replace("Software Engineering,", "~~Software Engineering~~,") \
             .replace("Java: The Complete Reference,", "~~Java: The Complete Reference~~,") \
             .replace("Fundamentals of Database Systems,", "~~Fundamentals of Database Systems~~,")
        add_runs(para, f"[{i}]\t{r}", 11)
        pf = para.paragraph_format
        pf.left_indent = Cm(1.0)
        pf.first_line_indent = Cm(-1.0)
        pf.space_after = Pt(8)
        pf.line_spacing = 1.0
        pf.alignment = WD_ALIGN_PARAGRAPH.LEFT


def annex(w):
    head = w.doc.add_paragraph(style="Heading 1")
    set_font(head.add_run("ANNEX"), 16, bold=True)
    head.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.CENTER
    head.paragraph_format.page_break_before = True
    head.paragraph_format.space_after = Pt(18)
    w.h2("Annex A: Source Code")
    w.p("The complete source code, the SQL schema, the seed data and the test suite are in the project repository.")
    w.p("**GitHub Repository:** [GitHub repository link]", first=False)
    w.p("The listings below are the parts most often asked about: the engine loop, the trigger that grows the problem "
        "bank, and the archive procedure.", first=True)
    w.label("A.1 SimilarityEngine.compare (Java)")
    w.code([
        "public Breakdown compare(Candidate idea, Candidate other) {",
        "    double t = tags.score(idea, other);",
        "    double k = keywords.score(idea, other);",
        "    double y = type.score(idea, other);",
        "    double total = 0;",
        "    // polymorphism: the loop never asks which scorer it has",
        "    for (Scorer s : scorers) {",
        "        total += s.weight() * s.score(idea, other);",
        "    }",
        "    return new Breakdown(other, t, k, y, total);",
        "}",
    ])
    w.label("A.2 Trigger: a student's problem joins the problem bank (MySQL)")
    w.code([
        "CREATE TRIGGER trg_check_problem",
        "AFTER INSERT ON idea_checks FOR EACH ROW",
        "BEGIN",
        "  IF NEW.problem IS NOT NULL",
        "     AND CHAR_LENGTH(TRIM(NEW.problem)) >= 25 THEN",
        "    INSERT IGNORE INTO problem_bank (domain_id, statement,",
        "        affected, solution_phrase, primary_type_id, source)",
        "    VALUES (NEW.domain_id, TRIM(NEW.problem),",
        "        'Reported by a student', NEW.title, NEW.type_id,",
        "        'STUDENT');",
        "  END IF;",
        "END",
    ])
    w.label("A.3 Stored procedure: close the semester (MySQL, abridged)")
    w.code([
        "CREATE PROCEDURE sp_archive_semester(",
        "    IN p_new_code VARCHAR(20), IN p_new_label VARCHAR(60),",
        "    IN p_actor INT, OUT p_moved INT)",
        "BEGIN",
        "  DECLARE EXIT HANDLER FOR SQLEXCEPTION",
        "    BEGIN ROLLBACK; RESIGNAL; END;",
        "  START TRANSACTION;",
        "  -- 1. approved ideas join the corpus",
        "  -- 2. their tags follow them",
        "  -- 3. this semester closes and the next one opens",
        "  -- 4. an audit row records the move",
        "  COMMIT;",
        "END",
    ])
    w.h2("Annex B: User Manual")
    w.p("Origin needs JDK 17 or newer, Apache Maven 3.9 and MySQL 8 running on port 3306.")
    w.bullets([
        "Step 1: Clone the repository: git clone [repository URL]",
        "Step 2: Install dependencies: Maven downloads them on the first build (mvn compile).",
        "Step 3: Configure the settings: copy config/origin.properties.example to config/origin.properties and enter the "
        "MySQL password. To enable the decision engine, also enter origin.engine.key.",
        "Step 4: Set up the database: nothing to run by hand. On first start Origin creates the origin database, the "
        "schema, the triggers, the procedures and the demo data.",
        "Step 5: Start the application: double-click run.bat, or run mvn package and deploy target/origin.war to Tomcat 10.1.",
        "Step 6: Access the application at: http://localhost:8080",
    ])
    w.p("Demo accounts use the password Origin@2026: students sandesh@origin.edu, bhumika@origin.edu and "
        "diwakar@origin.edu (group G09), students of groups G01 to G08 (for example aarati@origin.edu), and "
        "supervisor@origin.edu. To start again with fresh demo data, drop the origin database and start Origin once more.")
    w.p("A typical session: sign in, open Check an idea, fill in the form and press Check originality. Read the result, "
        "edit and check again if the idea overlaps, then lock it for the group. The supervisor signs in, reviews the "
        "locked ideas on the supervisor desk, and closes the semester when every group is approved.")


if __name__ == "__main__":
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    import report_v3
    report_v3.install(sys.modules[__name__])
    build(sys.argv[1], sys.argv[2])
    report_v3.verify()
