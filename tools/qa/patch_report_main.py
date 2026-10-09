"""One-time patch: build_report.py applies tools/report_v3.py before building."""
from pathlib import Path

root = Path(__file__).resolve().parent.parent
b = root / "build_report.py"
s = b.read_text(encoding="utf-8")
old = 'if __name__ == "__main__":\n    build(sys.argv[1], sys.argv[2])\n'
new = ('if __name__ == "__main__":\n    sys.path.insert(0, str(Path(__file__).resolve().parent))\n'
       '    import report_v3\n    report_v3.install(sys.modules[__name__])\n'
       '    build(sys.argv[1], sys.argv[2])\n    report_v3.verify()\n')
if old not in s:
    raise SystemExit("main block not found")
b.write_text(s.replace(old, new, 1), encoding="utf-8")

v = root / "report_v3.py"
t = v.read_text(encoding="utf-8")
marker = '# ---------------------------------------------------------------- bullet rules'
extra = '''rule_p("The listings below are the parts most often asked about",
       "The listings below are the parts most often asked about: the engine loop, the weighted overlap, the folder "
       "watcher, the trigger that grows the problem bank, and the archive procedure.")

'''
if "The listings below are the parts" not in t:
    t = t.replace(marker, extra + marker, 1)
    v.write_text(t, encoding="utf-8")
print("patched")
