// Warm response times, averaged over repeated requests, for the report's performance table.
import { launch } from './cdp.mjs';
const BASE = 'http://localhost:8080';
const b = await launch({ width: 1440, height: 900, port: 9339 });
const report = {};
try {
  await b.goto(BASE + '/login');
  await b.eval(`document.querySelector('input[name=email]').value='sandesh@origin.edu'; document.querySelector('input[name=password]').value='Origin@2026'; document.querySelector('input[name=email]').form.submit()`);
  await b.sleep(1200);
  await b.goto(BASE + '/app/check');
  report.warmMs = await b.eval(`(async () => {
    const N = 7;
    const avg = async (label, fn) => { await fn(); const times = []; for (let i = 0; i < N; i++) { const s = performance.now(); await fn(); times.push(performance.now() - s); } times.sort((a, b) => a - b); return [label, Math.round(times.reduce((a, b) => a + b) / N), 'median ' + Math.round(times[Math.floor(N / 2)])]; };
    const csrf = document.querySelector('input[name=_csrf]').value;
    const d = document.querySelector('#domainId').options[1].value, t = document.querySelector('#typeId').options[1].value;
    let n = 0;
    const post = () => fetch('/app/check', { method: 'POST', redirect: 'manual', body: new URLSearchParams({ _csrf: csrf, title: 'Performance probe number ' + (++n),
      abstractText: 'Households book a tanker and a scheduler orders the queue by shortest job first so small orders are never starved by large ones.',
      problem: '', tags: 'water, booking, queue, scheduling', domainId: d, typeId: t }) });
    const out = [];
    out.push(await avg('Landing page with the Syllabus Map', () => fetch('/').then(r => r.text())));
    out.push(await avg('Live peek while typing', () => fetch('/peek?q=' + encodeURIComponent('library management system where students borrow books with fines')).then(r => r.text())));
    out.push(await avg('Ideas page', () => fetch('/app/ideas').then(r => r.text())));
    out.push(await avg('Class Radar', () => fetch('/app/radar').then(r => r.text())));
    out.push(await avg('Check form', () => fetch('/app/check').then(r => r.text())));
    out.push(await avg('Submit a check (score, save in one transaction)', post));
    const hist = await fetch('/app/history').then(r => r.text());
    const id = (hist.match(/result\\?id=(\\d+)/) || [])[1];
    out.push(await avg('Evaluation screen', () => fetch('/app/evaluating?id=' + id).then(r => r.text())));
    out.push(await avg('Result page with stories', () => fetch('/app/result?id=' + id).then(r => r.text())));
    return out;
  })()`);
} catch (e) { report.failure = String(e.stack || e); }
console.log(JSON.stringify(report, null, 1));
await b.close();
process.exit(0);
