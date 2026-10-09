"""Shoots the pages changed tonight, for review. Usage: py tools/qa/review_shots.py"""
import sys
from pathlib import Path
from playwright.sync_api import sync_playwright

BASE = "http://localhost:8080"
OUT = Path("target/review")
OUT.mkdir(parents=True, exist_ok=True)


def shot(page, name, full=False):
    page.wait_for_timeout(700)
    page.screenshot(path=str(OUT / f"{name}.png"), full_page=full)
    print("saved", name)


with sync_playwright() as p:
    browser = p.chromium.launch(channel="chrome")
    errors = []
    for width, height, suffix in ((1440, 900, ""), (390, 844, "-m")):
        ctx = browser.new_context(viewport={"width": width, "height": height}, device_scale_factor=1.5)
        page = ctx.new_page()
        page.on("console", lambda m: errors.append(f"{suffix or 'desktop'}: {m.text}") if m.type == "error" else None)
        page.goto(BASE + "/", wait_until="networkidle")
        shot(page, f"home-fold{suffix}")
        shot(page, f"home-full{suffix}", full=True)
        page.goto(BASE + "/login", wait_until="networkidle")
        shot(page, f"login{suffix}")
        ctx.close()
    browser.close()
    print("CONSOLE ERRORS:", errors if errors else "none")
