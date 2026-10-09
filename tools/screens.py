"""Takes screenshots of every Origin page for review and for the report.

Usage: py tools/screens.py [out_dir]
Needs the app running on http://localhost:8080 and Playwright with Chromium installed.
"""
import sys
import time
from pathlib import Path
from playwright.sync_api import sync_playwright

BASE = "http://localhost:8080"
OUT = Path(sys.argv[1] if len(sys.argv) > 1 else "screens")
OUT.mkdir(parents=True, exist_ok=True)
PASSWORD = "Origin@2026"


def login(page, email):
    page.goto(BASE + "/login")
    page.fill("#email", email)
    page.fill("#password", PASSWORD)
    page.click("main form button[type=submit]")
    page.wait_for_load_state("networkidle")


def settled(page):
    """Waits until the background comparison has finished and the result page has reloaded."""
    try:
        page.wait_for_selector(".refining", state="detached", timeout=40000)
    except Exception:
        print("warning: result still refining")
    page.wait_for_load_state("networkidle")


def shot(page, name, full=True):
    page.wait_for_timeout(900)  # let bars finish their fill transition
    page.screenshot(path=str(OUT / f"{name}.png"), full_page=full)
    print("saved", name)


def run(width, height, suffix):
    with sync_playwright() as p:
        browser = p.chromium.launch(channel="chrome")
        ctx = browser.new_context(viewport={"width": width, "height": height}, device_scale_factor=1.5)
        page = ctx.new_page()
        errors = []
        page.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)

        page.goto(BASE + "/")
        shot(page, f"01-home-fold{suffix}", full=False)
        shot(page, f"02-home-full{suffix}")
        page.goto(BASE + "/login")
        shot(page, f"03-login{suffix}", full=False)
        page.goto(BASE + "/register")
        shot(page, f"04-register{suffix}", full=False)

        login(page, "sandesh@origin.edu")
        page.goto(BASE + "/app/check")
        page.select_option("#domainId", "3")
        page.select_option("#typeId", "4")
        shot(page, f"05-check-form{suffix}")

        # A real check: a renamed library system, to show the rename catch.
        page.fill("#title", "Book Depot Portal")
        page.select_option("#domainId", "1")
        page.select_option("#typeId", "1")
        page.fill("#abstractText", "Students reserve books online from the college collection and get a reminder "
                                   "before the return date. Librarians see who has which book.")
        page.fill("#tags", "books, lending, reservation, notification")
        page.click("main form button[type=submit]")
        page.wait_for_load_state("networkidle")
        settled(page)
        shot(page, f"06-result-duplicate{suffix}")

        page.goto(BASE + "/app/check")
        page.fill("#title", "Health-Post Medicine Stock Alert Network")
        page.select_option("#domainId", "2")
        page.select_option("#typeId", "1")
        page.fill("#abstractText", "Health post in-charges enter stock levels of essential medicines each week. "
                                   "When a medicine falls below two weeks of use, the district store gets an alert "
                                   "and a map shows which posts are running low.")
        page.fill("#problem", "Rural health posts run out of essential medicines before the district store knows.")
        page.fill("#tags", "medicine, inventory, notification, dashboard")
        page.click("main form button[type=submit]")
        page.wait_for_load_state("networkidle")
        settled(page)
        shot(page, f"07-result-original{suffix}")

        page.goto(BASE + "/app/gaps")
        shot(page, f"08-gaps{suffix}")
        page.goto(BASE + "/app/gaps?d=9&t=4")
        shot(page, f"09-gaps-cell{suffix}")
        page.goto(BASE + "/app/ideas")
        shot(page, f"10-ideas{suffix}")
        page.goto(BASE + "/app/radar")
        shot(page, f"11-radar{suffix}")
        page.goto(BASE + "/app/history")
        shot(page, f"12-history{suffix}")

        ctx.clear_cookies()
        login(page, "supervisor@origin.edu")
        page.goto(BASE + "/admin")
        shot(page, f"13-admin{suffix}")

        if errors:
            print("CONSOLE ERRORS:", errors)
        browser.close()


if __name__ == "__main__":
    run(1440, 900, "")
    if "--mobile" in sys.argv:
        run(390, 844, "-m")
