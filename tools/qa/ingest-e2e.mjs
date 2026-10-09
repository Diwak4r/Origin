// Drops real files into the DB folder of the running app and watches the supervisor page.
// Usage: node ingest-e2e.mjs <folder-with-files-to-drop>
import { launch } from './cdp.mjs';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const out = path.join(here, 'shots');
const dropFrom = process.argv[2];
const dbFolder = path.resolve(here, '..', '..', 'DB');
const BASE = 'http://localhost:8080';
const b = await launch({ width: 1440, height: 900, port: 9336 });
const report = {};

async function stats() {
  return b.eval(`(() => ({
    projects: document.querySelectorAll('.stats .big')[2]?.textContent,
    filesRead: document.querySelectorAll('.stats .big')[4]?.textContent,
    log: [...document.querySelectorAll('.folder-path ~ ul li, .folder-path ~ p.empty')].map(li => li.innerText.replace(/\\s+/g, ' ').trim())
  }))()`);
}

try {
  await b.goto(BASE + '/login');
  await b.eval(`document.querySelector('input[name=email]').value='supervisor@origin.edu'; document.querySelector('input[name=password]').value='Origin@2026'; document.querySelector('input[name=email]').form.submit()`);
  await b.sleep(1200);
  await b.goto(BASE + '/admin');
  report.before = await stats();
  await b.goto(BASE + '/');
  report.mapBefore = await b.eval(`[...document.querySelectorAll('.plot')].map(p => p.dataset.name + '=' + p.querySelector('.plot-n').textContent).join('; ')`);

  fs.mkdirSync(dbFolder, { recursive: true });
  const t0 = Date.now();
  for (const f of fs.readdirSync(dropFrom)) fs.copyFileSync(path.join(dropFrom, f), path.join(dbFolder, f));
  report.dropped = fs.readdirSync(dropFrom);

  let seen = null;
  for (let i = 0; i < 20; i++) {
    await b.sleep(2000);
    await b.goto(BASE + '/admin', 100);
    const s = await stats();
    if (s.log.length >= report.dropped.length && s.log.every((l) => !/No file has been read/.test(l))) { seen = s; report.secondsUntilVisible = ((Date.now() - t0) / 1000).toFixed(1); break; }
  }
  report.after = seen || (await stats());
  await b.shot(path.join(out, 'ingest-1-admin.png'));
  await b.sleep(1500);
  await b.goto(BASE + '/');
  report.mapAfter = await b.eval(`[...document.querySelectorAll('.plot')].map(p => p.dataset.name + '=' + p.querySelector('.plot-n').textContent).join('; ')`);
  await b.shot(path.join(out, 'ingest-2-map.png'));
} catch (e) { report.failure = String(e.stack || e); }
report.consoleErrors = b.errors;
console.log(JSON.stringify(report, null, 1));
await b.close();
process.exit(0);
