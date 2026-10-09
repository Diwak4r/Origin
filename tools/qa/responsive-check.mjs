// Phone width and dark mode checks for the home page and the signed-in pages.
import { launch } from './cdp.mjs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const out = path.join(path.dirname(fileURLToPath(import.meta.url)), 'shots');
const BASE = 'http://localhost:8080';
const b = await launch({ width: 390, height: 844, port: 9335 });
const report = {};
async function overflow() {
  return b.eval(`(() => { const w = document.documentElement.clientWidth; const bad = [...document.querySelectorAll('body *')].filter(e => { const r = e.getBoundingClientRect(); return r.width > 0 && (r.right > w + 1 || r.left < -1); }).slice(0, 5).map(e => e.tagName + '.' + e.className); return { page: document.documentElement.scrollWidth > w, offenders: bad }; })()`);
}
async function login(email) {
  await b.goto(BASE + '/login');
  await b.eval(`document.querySelector('input[name=email]').value=${JSON.stringify(email)}; document.querySelector('input[name=password]').value='Origin@2026'; document.querySelector('input[name=email]').form.submit()`);
  await b.sleep(1200);
}
try {
  await b.viewport(390, 844, true);
  await b.goto(BASE + '/');
  report.homePhone = await overflow();
  await b.type('#idea', 'Library management system where students borrow and return books', 3);
  await b.sleep(1100);
  await b.shot(path.join(out, 'phone-1-home.png'));
  await b.eval(`document.getElementById('map').scrollIntoView()`);
  await b.sleep(300);
  await b.shot(path.join(out, 'phone-2-map.png'));

  await b.send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-color-scheme', value: 'dark' }] });
  await b.viewport(1440, 900, false);
  await b.goto(BASE + '/');
  await b.type('#idea', 'Library management system where students borrow and return books with late fines and reminders', 3);
  await b.sleep(1200);
  await b.shot(path.join(out, 'dark-1-home.png'));

  await login('sandesh@origin.edu');
  await b.goto(BASE + '/app/ideas');
  await b.shot(path.join(out, 'dark-2-ideas.png'));
  await b.viewport(390, 844, true);
  await b.goto(BASE + '/app/ideas');
  report.ideasPhone = await overflow();
  await b.send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-color-scheme', value: 'light' }] });
  await b.goto(BASE + '/app/history');
  report.historyPhone = await overflow();
  await b.goto(BASE + '/app/radar');
  report.radarPhone = await overflow();
} catch (e) { report.failure = String(e.stack || e); }
report.consoleErrors = b.errors;
console.log(JSON.stringify(report, null, 1));
await b.close();
process.exit(0);
