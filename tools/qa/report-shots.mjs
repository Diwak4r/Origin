// Captures every page used in the report, at 1.5x, in the real scenarios (typing, checks, a file drop).
// Writes full-page PNGs to docs/screens; tools/crop_report_images.py crops them into docs/report-img.
// Needs the app running with the demo data. It creates two checks and drops two files: clean up afterwards.
import { launch } from './cdp.mjs';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.resolve(here, '..', '..', 'docs', 'screens');
const DB = path.resolve(here, '..', '..', 'DB');
const dropFrom = process.argv[2];
const BASE = 'http://localhost:8080';
const b = await launch({ width: 1440, height: 900, port: 9340, scale: 1.5 });
await b.send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-color-scheme', value: 'light' }] });
fs.mkdirSync(OUT, { recursive: true });
const report = {};
const out = (n) => path.join(OUT, n);

async function login(email) {
  await b.goto(BASE + '/login');
  await b.eval(`document.querySelector('input[name=email]').value=${JSON.stringify(email)}; document.querySelector('input[name=password]').value='Origin@2026'; document.querySelector('input[name=email]').form.submit()`);
  await b.sleep(1300);
}
async function waitPath(re, ms = 15000) {
  const t0 = Date.now();
  while (Date.now() - t0 < ms) { if (re.test(await b.eval('location.pathname'))) return true; await b.sleep(80); }
  return false;
}
async function fillAndSubmit(f) {
  await b.eval(`
    document.querySelector('#title').value = ${JSON.stringify(f.title)};
    document.querySelector('#abstractText').value = ${JSON.stringify(f.abstract)};
    document.querySelector('#problem').value = ${JSON.stringify(f.problem || '')};
    document.querySelector('#tags').value = ${JSON.stringify(f.tags)};
    const d = document.querySelector('#domainId'); d.value = [...d.options].find(o => o.text.startsWith(${JSON.stringify(f.domain)})).value;
    const t = document.querySelector('#typeId'); t.value = [...t.options].find(o => o.text.startsWith(${JSON.stringify(f.type)})).value;
    d.dispatchEvent(new Event('change')); t.dispatchEvent(new Event('change'));`);
  await b.sleep(400);
}
async function resultNumbers() {
  return b.eval(`({ pct: document.querySelector('.verdict .pct')?.textContent, label: document.querySelector('.verdict .label')?.textContent,
    fix: document.querySelector('.fix')?.textContent, cover: document.querySelector('.coverline')?.textContent,
    rows: [...document.querySelectorAll('.matches')][0] ? [...document.querySelectorAll('.matches')[0].querySelectorAll('tbody tr')].map(tr => [...tr.querySelectorAll('td')].map(td => td.innerText.replace(/\\s+/g,' ').trim()).join(' | ')) : [],
    classRows: [...document.querySelectorAll('.matches')][1] ? [...document.querySelectorAll('.matches')[1].querySelectorAll('tbody tr')].map(tr => [...tr.querySelectorAll('td')].map(td => td.innerText.replace(/\\s+/g,' ').trim()).join(' | ')) : [] })`);
}

try {
  // ---- public pages
  await b.goto(BASE + '/');
  await b.type('#idea', 'Library management system where students borrow and return books with late fines and reminders', 4);
  await b.sleep(1500);
  report.homeHot = await b.eval(`({ num: document.getElementById('ro-num').textContent, label: document.getElementById('ro-label').textContent, line: document.getElementById('ro-line').textContent, hot: document.querySelectorAll('.plot.is-hot').length })`);
  await b.shot(out('01-home-fold.png'));
  await b.eval(`document.getElementById('idea').value=''; document.getElementById('idea').dispatchEvent(new Event('input'))`);
  await b.type('#idea', "Allocate donated blankets to shelters with the Banker's algorithm so nobody waits forever", 4);
  await b.sleep(1500);
  report.homeOpen = await b.eval(`({ num: document.getElementById('ro-num').textContent, label: document.getElementById('ro-label').textContent, line: document.getElementById('ro-line').textContent, mine: [...document.querySelectorAll('.plot.is-mine')].map(p => p.dataset.name) })`);
  await b.shot(out('14-map-open.png'));
  await b.eval(`[...document.querySelectorAll('.plot')].find(p => p.dataset.name.startsWith('Deadlock')).click()`);
  await b.sleep(900);
  await b.eval(`window.scrollTo(0, 0)`);
  await b.shot(out('16-map-filter.png'), { full: true });
  await b.eval(`document.getElementById('idea').value=''; document.getElementById('idea').dispatchEvent(new Event('input')); [...document.querySelectorAll('.plot.sel')].forEach(p => p.click())`);
  await b.sleep(600);
  await b.shot(out('02-home-full.png'), { full: true });
  await b.goto(BASE + '/login');
  await b.shot(out('03-login.png'));

  // ---- signed-in pages
  await login('sandesh@origin.edu');
  await b.goto(BASE + '/');
  await b.type('#idea', 'Book Depot Portal', 20);
  await b.click('#stake-form button[type=submit]');
  await b.sleep(1200);
  await fillAndSubmit({
    title: 'Book Depot Portal', domain: 'Education', type: 'Web',
    abstract: 'Students reserve books online from the college collection and get a reminder before the return date. Librarians see who has which book.',
    tags: 'books, lending, reservation, notification' });
  await b.shot(out('05-check-form.png'), { full: true });
  await b.eval(`document.querySelector('#abstractText').form.submit()`);
  await waitPath(/\/app\/evaluating/);
  await b.sleep(1250);
  await b.shot(out('15-evaluating.png'));
  await waitPath(/\/app\/result/);
  await b.sleep(1300);
  report.duplicate = await resultNumbers();
  await b.shot(out('06-result-duplicate.png'), { full: true });

  await b.goto(BASE + '/app/check');
  await fillAndSubmit({
    title: 'Health-Post Medicine Stock Alert Network', domain: 'Health', type: 'Web',
    abstract: 'Health post in-charges enter stock levels of essential medicines each week. When a medicine falls below two weeks of use, the district store gets an alert and a map shows which posts are running low.',
    problem: 'Rural health posts run out of essential medicines before the district store knows.',
    tags: 'medicine, inventory, notification, dashboard' });
  await b.eval(`document.querySelector('#abstractText').form.submit()`);
  await waitPath(/\/app\/result/, 20000);
  await b.sleep(1500);
  report.original = await resultNumbers();
  await b.shot(out('07-result-original.png'), { full: true });

  await b.goto(BASE + '/app/gaps?d=9&t=4');
  await b.shot(out('09-gaps-cell.png'), { full: true });
  await b.goto(BASE + '/app/ideas');
  report.ideasTop = await b.eval(`[...document.querySelectorAll('.idea h3')].slice(0, 4).map(h => h.textContent)`);
  await b.shot(out('10-ideas.png'), { full: true });
  await b.goto(BASE + '/app/radar');
  await b.shot(out('11-radar.png'), { full: true });
  await b.goto(BASE + '/app/history');
  await b.shot(out('12-history.png'), { full: true });

  // ---- phone
  await b.viewport(390, 844, true);
  await b.goto(BASE + '/');
  await b.type('#idea', 'Library management system where students borrow and return books', 3);
  await b.sleep(1300);
  await b.shot(out('01-home-fold-m.png'));
  await b.goto(BASE + '/app/history');
  const lastId = await b.eval(`(document.querySelector('a[href*="result?id="]').href.match(/id=(\\d+)/) || [])[1]`);
  await b.goto(BASE + '/app/result?id=' + lastId);
  await b.sleep(1000);
  await b.shot(out('07-result-original-m.png'), { full: true });
  await b.goto(BASE + '/app/ideas');
  await b.shot(out('10-ideas-m.png'), { full: true });
  await b.viewport(1440, 900, false);

  // ---- supervisor desk after a real file drop
  await b.eval(`fetch('/logout', { method: 'POST', body: new URLSearchParams({ _csrf: document.querySelector('input[name=_csrf]')?.value || '' }) })`).catch(() => {});
  await b.sleep(500);
  await login('supervisor@origin.edu');
  if (dropFrom) {
    for (const f of fs.readdirSync(dropFrom)) fs.copyFileSync(path.join(dropFrom, f), path.join(DB, f));
    for (let i = 0; i < 20; i++) {
      await b.sleep(2000);
      await b.goto(BASE + '/admin', 100);
      const n = await b.eval(`document.querySelectorAll('.folder-path ~ ul li').length`);
      if (n >= 2) break;
    }
    await b.sleep(1500);
  }
  await b.goto(BASE + '/admin');
  report.admin = await b.eval(`[...document.querySelectorAll('.stats .big')].map(e => e.textContent)`);
  await b.shot(out('13-admin.png'), { full: true });
} catch (e) { report.failure = String(e.stack || e); }
report.consoleErrors = b.errors;
console.log(JSON.stringify(report, null, 1));
await b.close();
process.exit(0);
