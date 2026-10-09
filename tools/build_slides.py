"""Fills the college slide template (slidetemplateforProposal.pptx) for Origin.

Usage: py tools/build_slides.py <template.pptx> <out.pptx>
"""
import copy
import sys
from pathlib import Path

from PIL import Image
from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.oxml.ns import qn
from pptx.util import Cm, Pt
from lxml import etree

ROOT = Path(__file__).resolve().parent.parent
DIAG = ROOT / "docs" / "diagrams"
IMG = ROOT / "docs" / "report-img"

TITLE = "“ORIGIN: A PROJECT GAP AND SIMILARITY FINDER”"
NAMES = ["Sandesh Dotel [Roll No.]", "Bhumika Karki [Roll No.]", "Diwakar Ray Yadav [Roll No.]"]
DATE = "September 2026"


def bullet(para, kind="dot", level=0):
    """Sets the bullet explicitly, so no paragraph inherits the template's leftover bullet style.
    kind: "dot" (bullet), "none" (plain label) or "hang" (numbered reference with a hanging indent)."""
    ppr = para._p.get_or_add_pPr()
    for tag in ("a:buNone", "a:buChar", "a:buAutoNum", "a:buFont", "a:buSzPct", "a:buSzPts", "a:buSzTx"):
        for el in ppr.findall(qn(tag)):
            ppr.remove(el)
    anchor = None
    for tag in ("a:tabLst", "a:defRPr", "a:extLst"):
        anchor = ppr.find(qn(tag))
        if anchor is not None:
            break

    def add(tag, **attrs):
        el = etree.Element(qn(tag))
        for k, v in attrs.items():
            el.set(k, v)
        if anchor is not None:
            anchor.addprevious(el)
        else:
            ppr.append(el)

    if kind == "dot":
        ppr.set("marL", str(int(Cm(0.9 + 0.9 * level))))
        ppr.set("indent", str(int(-Cm(0.6))))
        add("a:buSzPct", val="100000")
        add("a:buFont", typeface="Arial")
        add("a:buChar", char="•")
    elif kind == "hang":
        ppr.set("marL", str(int(Cm(1.0))))
        ppr.set("indent", str(int(-Cm(1.0))))
        add("a:buNone")
    else:
        ppr.set("marL", str(int(Cm(0.9 * level))))
        ppr.set("indent", "0")
        add("a:buNone")


def set_body(shape, items, size=20, kind="dot"):
    """items: list of str (level 0) or (str, level). A line starting with ** is a bold label without a bullet."""
    tf = shape.text_frame
    tf.clear()
    tf.word_wrap = True
    first = True
    for it in items:
        text, level = (it, 0) if isinstance(it, str) else it
        para = tf.paragraphs[0] if first else tf.add_paragraph()
        first = False
        para.level = level
        label = text.startswith("**") and text.endswith(":")
        run = para.add_run()
        run.text = text.strip("*")
        run.font.size = Pt(size - 2 * level)
        run.font.bold = True if label else None
        para.space_after = Pt(6)
        bullet(para, "none" if label else kind, level)


def picture(slide, path, top=3.4, bottom=17.3, left=1.2, right=32.6):
    with Image.open(path) as im:
        w, h = im.size
    box_w, box_h = right - left, bottom - top
    scale = min(box_w / w, box_h / h)
    pw, ph = w * scale, h * scale
    slide.shapes.add_picture(str(path), Cm(left + (box_w - pw) / 2), Cm(top + (box_h - ph) / 2), Cm(pw), Cm(ph))


def body(slide):
    for sh in slide.placeholders:
        if sh.placeholder_format.idx == 1:
            return sh
    return None


def set_dates(slide):
    for sh in slide.placeholders:
        if sh.placeholder_format.type is not None and "DATE" in str(sh.placeholder_format.type):
            sh.text_frame.text = DATE


def table(slide, rows, widths, top=4.6, size=15):
    left = Cm(2.0)
    shape = slide.shapes.add_table(len(rows), len(rows[0]), left, Cm(top), Cm(sum(widths)), Cm(1.0 * len(rows)))
    t = shape.table
    for j, w in enumerate(widths):
        t.columns[j].width = Cm(w)
    for i, row in enumerate(rows):
        for j, v in enumerate(row):
            cell = t.cell(i, j)
            cell.text = v
            for para in cell.text_frame.paragraphs:
                for r in para.runs:
                    r.font.size = Pt(size)
                    r.font.bold = (i == 0)
                    if i == 0:
                        r.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
            if i == 0:
                cell.fill.solid()
                cell.fill.fore_color.rgb = RGBColor(0x12, 0x35, 0x2A)
            else:
                cell.fill.solid()
                cell.fill.fore_color.rgb = RGBColor(0xF3, 0xF6, 0xF2) if i % 2 else RGBColor(0xFF, 0xFF, 0xFF)
    return t


def build(template, out):
    prs = Presentation(template)
    s = prs.slides

    # 1 Title
    for sh in s[0].shapes:
        if not sh.has_text_frame:
            continue
        for para in sh.text_frame.paragraphs:
            for r in para.runs:
                if "TITLE OF THE PROJECT" in r.text:
                    r.text = r.text.replace("“TITLE OF THE PROJECT”", TITLE).replace('"TITLE OF THE PROJECT"', TITLE)
                if r.text.strip().startswith("Semester:"):
                    r.text = "Semester: IV"
        names = iter(NAMES)
        for para in sh.text_frame.paragraphs:
            for r in para.runs:
                if "Name1" in r.text:
                    r.text = next(names, r.text)
    set_dates(s[0])

    # 2 Table of contents
    set_body(body(s[1]), ["Introduction and problem statement", "Objectives and scope", "Feasibility and literature review",
                          "SDLC model and system architecture", "System flow, use case and data flow diagrams",
                          "ER diagram", "Hardware and software requirements", "Outcome", "References"], 20)

    # 3 Introduction
    set_body(body(s[2]), [
        "BIT groups choose a project idea every semester, and the same ideas keep coming back: library, attendance, hospital and hotel systems.",
        "Origin checks an idea at proposal time against past projects and this semester's locked ideas, and explains every score.",
        "A Syllabus Map of 41 topics reacts as you type, and the Ideas page suggests real problems built from topics nobody has used.",
        "Past projects load themselves from files dropped into a folder. Built with Java Servlets and JSP on Tomcat and MySQL.",
    ], 20)

    # 4 Problem statement
    set_body(body(s[3]), [
        "Repeated ideas are found late: at the proposal defense or the mid-term, after weeks of work.",
        "Renaming hides an old idea (\"Library System\" becomes \"Book Depot Portal\"), and buzzwords like \"AI\" make it look new.",
        "Tools such as Turnitin check finished text, not ideas, and none shows which areas are still unexplored.",
    ], 22)

    # 5 Objectives
    set_body(body(s[4]), [
        "**General Objective:",
        ("To build a web application that loads past projects from files, checks an idea against them and current projects, shows untouched syllabus topics and suggests real problems to build.", 1),
        "**Specific Objectives:",
        ("Study how BIT ideas repeat and review existing originality tools.", 1),
        ("Design a layered architecture and a self-updating MySQL schema.", 1),
        ("Build an explainable similarity engine that resists renaming and buzzwords.", 1),
        ("Build the Syllabus Map and the idea suggester.", 1),
        ("Implement Class Radar, supervisor review, semester archiving and the DB folder.", 1),
        ("Test for correctness, security and performance.", 1),
    ], 20)

    # 6 Scope
    set_body(body(s[5]), [
        "Idea check against the past-project corpus and this semester's locked ideas, with a score split into tags, words and type, told as a story.",
        "Class Radar: one locked idea per group per semester; clashes between groups shown to both.",
        "Syllabus Map, gap map and ranked idea suggestions; DB-folder import of CSV, TXT, XLSX and DOCX; supervisor review and semester archiving.",
        "Not included: PDF files, checking finished reports or code for copied text, mobile app, multi-college use.",
    ], 20)

    # 7 Feasibility
    set_body(body(s[6]), [
        "**Technical: Java, Servlets, JSP, JDBC and MySQL are all in the 4th-semester syllabus and are free.",
        "**Operational: students enter what they already write in a proposal; no training needed.",
        "**Economic: all tools are free; no paid service is needed.",
        "**Schedule: five increments from February to October 2026.",
    ], 20)
    # the bold marker above covers the whole line; make only the label bold
    for para in body(s[6]).text_frame.paragraphs:
        r = para.runs[0]
        label, rest = r.text.split(":", 1)
        r.text = label + ":"
        r.font.bold = True
        extra = para.add_run()
        extra.text = rest
        extra.font.size = r.font.size
        extra.font.bold = False
        bullet(para, "dot")

    # 8 Literature review
    sp = body(s[7])
    sp._element.getparent().remove(sp._element)
    table(s[7], [
        ["System", "What it checks", "What it cannot do"],
        ["Turnitin, iThenticate", "Finished writing", "Judge an idea before it is written"],
        ["MOSS, JPlag", "Finished source code", "Say anything about the idea itself"],
        ["DSpace repository", "Stores past reports", "Score similarity or show gaps"],
        ["Google Scholar", "Published research", "See a college's own student projects"],
        ["Supervisor memory", "Common repeats", "Scale, or catch same-week clashes"],
        ["Origin (this project)", "The idea, at proposal time", "Replace a check of finished text"],
    ], [8.0, 9.5, 12.3], top=4.4, size=16)

    # 9 to 15 diagrams
    for idx, name in [(8, "fig-sdlc.png"), (9, "fig-architecture.png"), (10, "fig-flowchart.png"),
                      (11, "fig-usecase.png"), (12, "fig-dfd0.png"), (13, "fig-dfd1.png"), (14, "fig-er.png")]:
        slide = s[idx]
        bp = body(slide)
        if bp is not None:
            bp._element.getparent().remove(bp._element)
        title = slide.shapes.title
        # below the title text; the title boxes are taller than their text, so cap at 3.4 cm
        top = min(3.4, max(2.6, (title.top + title.height) / 360000 + 0.1)) if title is not None else 3.4
        bottom = 18.4 if idx in (9, 10, 11) else 17.3   # tall portrait diagrams may use the footer band centre
        picture(slide, DIAG / name, top=top, bottom=bottom)

    # 16 Hardware and software
    sp = body(s[15])
    sp._element.getparent().remove(sp._element)
    table(s[15], [
        ["Component", "Specification"],
        ["Processor / RAM", "Intel Core i3 or better, 4 GB RAM (8 GB recommended)"],
        ["Operating system", "Windows 10/11 or Ubuntu 20.04+"],
        ["Language and container", "Java 17, Apache Tomcat 10.1 (Servlets 6.0, JSP 3.1, JSTL)"],
        ["Database", "MySQL 8.4 through JDBC (Connector/J)"],
        ["Build and test", "Maven 3.9, JUnit 5, Chrome DevTools driver (Node)"],
        ["Tools", "IntelliJ IDEA / VS Code, Git and GitHub, Chrome"],
        ["File reading", "java.io and java.util.zip for CSV, TXT, XLSX and DOCX (no extra library)"],
    ], [9.0, 20.8], top=4.4, size=16)

    # 17 Outcome
    set_body(body(s[16]), [
        "Working system: Syllabus Map, idea check, ideas with proof, Class Radar, DB-folder import and supervisor desk.",
        "Renamed library idea caught at 62%; the formula agrees with human labels on 22 of 25 pairs, as often as the old one.",
        "DB folder: a real Excel file and a CSV were read in 8 seconds and the map updated on its own.",
        "84 suggestions from 42 real problems, each with proof; 7 taken by locked groups; 25 tables, 6 triggers, 4 views, 2 procedures.",
        "84 automated tests pass; every page answers in under 0.4 s.",
    ], 20)

    # 18 References
    set_body(body(s[17]), [
        "[1] Turnitin, LLC, \"Turnitin Similarity.\" [Online]. Available: https://www.turnitin.com",
        "[2] S. Schleimer, D. S. Wilkerson, and A. Aiken, \"Winnowing: Local algorithms for document fingerprinting,\" in Proc. ACM SIGMOD, 2003, pp. 76-85.",
        "[3] P. Jaccard, \"The distribution of the flora in the alpine zone,\" New Phytologist, vol. 11, no. 2, pp. 37-50, 1912.",
        "[4] A. Silberschatz, H. F. Korth, and S. Sudarshan, Database System Concepts, 7th ed. McGraw-Hill, 2019.",
        "[5] Eclipse Foundation, \"Jakarta Servlet Specification, Version 6.0.\" [Online]. Available: https://jakarta.ee",
        "[6] C. D. Manning, P. Raghavan, and H. Schutze, Introduction to Information Retrieval. Cambridge Univ. Press, 2008.",
        "[7] I. Sommerville, Software Engineering, 10th ed. Pearson, 2016.",
        "[8] H. Schildt, Java: The Complete Reference, 12th ed. McGraw-Hill, 2021.",
    ], 15, kind="hang")

    # template slides 16 and 17 have no date box; copy the one from the nearest slide that has it
    def date_box(slide):
        return next((sh for sh in slide.placeholders if "DATE" in str(sh.placeholder_format.type)), None)
    src = date_box(s[14])
    for sl in (s[15], s[16]):
        if date_box(sl) is None and src is not None:
            sl.shapes._spTree.append(copy.deepcopy(src._element))
            dst = date_box(sl)
            dst.left, dst.top, dst.width, dst.height = src.left, src.top, src.width, src.height

    # new slide after the ER diagram: the Syllabus Map (a real screenshot of the running system).
    # Its title, date and slide number are copies of the ER slide's, so they look like the template's own.
    er_slide, hw_slide = s[14], s[15]
    new = prs.slides.add_slide(hw_slide.slide_layout)
    for ph in list(new.placeholders):
        ph._element.getparent().remove(ph._element)
    for ph in er_slide.placeholders:
        new.shapes._spTree.append(copy.deepcopy(ph._element))
    title = new.shapes.title
    runs = [r for para in title.text_frame.paragraphs for r in para.runs]
    runs[0].text = "Syllabus Map"
    for r in runs[1:]:
        r.text = ""
    picture(new, IMG / "f51-home.png", top=min(3.4, max(2.6, (title.top + title.height) / 360000 + 0.1)), bottom=17.3)
    ids = prs.slides._sldIdLst
    entry = ids[-1]
    ids.remove(entry)
    ids.insert(15, entry)

    for sl in prs.slides:
        set_dates(sl)
    prs.save(out)
    print("saved", out)


if __name__ == "__main__":
    build(sys.argv[1], sys.argv[2])
