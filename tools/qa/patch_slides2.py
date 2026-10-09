"""One-time patch: the new Syllabus Map slide copies its title, date and slide number from the ER slide."""
from pathlib import Path

p = Path(__file__).resolve().parent.parent / "build_slides.py"
s = p.read_text(encoding="utf-8")
start = s.index("    # new slide after the ER diagram")
end = s.index("    ids = prs.slides._sldIdLst")
new_block = '''    # new slide after the ER diagram: the Syllabus Map (a real screenshot of the running system).
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
'''
s = s[:start] + new_block + s[end:]
p.write_text(s, encoding="utf-8")
print("slides patched again")
