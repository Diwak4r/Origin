"""Crops the page screenshots in docs/screens into the figures used by the report (docs/report-img).

The screenshots come from tools/qa/report-shots.mjs (1.5x, Chrome). Usage: py tools/crop_report_images.py
"""
from pathlib import Path
from PIL import Image

S = Path(__file__).resolve().parent.parent / "docs" / "screens"
O = Path(__file__).resolve().parent.parent / "docs" / "report-img"
O.mkdir(exist_ok=True)

CROPS = {
    "f41-login.png": ("03-login.png", (0, 0, 2160, 1350)),
    "f42-check.png": ("05-check-form.png", (0, 100, 2138, 1900)),
    "f43-result-dup.png": ("06-result-duplicate.png", (0, 100, 2138, 2760)),
    "f44-result-orig.png": ("07-result-original.png", (0, 100, 2138, 1850)),
    "f45-gap-cell.png": ("09-gaps-cell.png", (0, 1150, 2138, 2000)),
    "f46-ideas.png": ("10-ideas.png", (0, 100, 2138, 1900)),
    "f47-radar.png": ("11-radar.png", (0, 100, 2138, 1500)),
    "f48-admin.png": ("13-admin.png", (0, 100, 2138, 1800)),
    "f51-home.png": ("01-home-fold.png", (0, 0, 2160, 1350)),
    "f52-rename.png": ("02-home-full.png", (0, 3540, 2138, 4200)),
    "f53-history.png": ("12-history.png", (0, 100, 2138, 1000)),
    "f55-map-open.png": ("14-map-open.png", (0, 0, 2160, 1350)),
    "f56-evaluating.png": ("15-evaluating.png", (0, 60, 2160, 1000)),
    "f57-map-filter.png": ("16-map-filter.png", (0, 650, 2138, 2450)),
}

for out, (src, (l, t, r, b)) in CROPS.items():
    im = Image.open(S / src)
    im.crop((l, t, r, min(b, im.height))).save(O / out)

phones = [Image.open(S / n) for n in ("01-home-fold-m.png", "07-result-original-m.png", "10-ideas-m.png")]
H = 1700
phones = [p.crop((0, 0, p.width, min(H, p.height))) for p in phones]
sheet = Image.new("RGB", (sum(p.width for p in phones) + 80, H), "white")
x = 0
for p in phones:
    sheet.paste(p, (x, 0))
    x += p.width + 40
sheet.save(O / "f54-mobile.png")
print("cropped", len(CROPS) + 1, "images")
