"""One-time patch: scale and clip support for the browser driver."""
from pathlib import Path

p = Path(__file__).resolve().parent / "cdp.mjs"
s = p.read_text(encoding="utf-8")


def sub(a, b):
    global s
    if a not in s:
        raise SystemExit("missing: " + a[:90])
    s = s.replace(a, b, 1)


sub("export async function launch({ width = 1440, height = 900, port = 9333 } = {}) {",
    "export async function launch({ width = 1440, height = 900, port = 9333, scale = 1 } = {}) {\n  const dsf = scale;")
sub("await send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false });\n\n  const api",
    "await send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: dsf, mobile: false });\n\n  const api")
sub("async viewport(w, h, mobile = false) { await send('Emulation.setDeviceMetricsOverride', { width: w, height: h, deviceScaleFactor: 1, mobile }); },",
    "async viewport(w, h, mobile = false) { await send('Emulation.setDeviceMetricsOverride', { width: w, height: h, deviceScaleFactor: dsf, mobile }); },")
sub("    async type(selector", """    async shotClip(file, clip) {
      fs.mkdirSync(path.dirname(file), { recursive: true });
      const r = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true, clip: { ...clip, scale: 1 } });
      fs.writeFileSync(file, Buffer.from(r.data, 'base64'));
      return file;
    },
    async rect(selector) { return api.eval(`(() => { const r = document.querySelector(${JSON.stringify(selector)}).getBoundingClientRect(); return { x: r.left + scrollX, y: r.top + scrollY, width: r.width, height: r.height }; })()`); },
    async type(selector""")
p.write_text(s, encoding="utf-8")
print("cdp patched")
