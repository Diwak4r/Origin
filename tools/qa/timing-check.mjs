// Where does the time go between submitting a check and seeing the result?
import { launch } from './cdp.mjs';
const BASE = 'http://localhost:8080';
const b = await launch({ width: 1440, height: 900, port: 9338 });
const report = {};
try {
  await b.goto(BASE + '/login');
  await b.eval(`document.querySelector('input[name=email]').value='sandesh@origin.edu'; document.querySelector('input[name=password]').value='Origin@2026'; document.querySelector('input[name=email]').form.submit()`);
  await b.sleep(1200);

  // time the server pieces with plain fetches from inside the signed-in page
  await b.goto(BASE + '/app/check?title=Timing+probe+for+the+tanker+queue+idea');
  report.serverMs = await b.eval(`(async () => {
    const t = async (label, fn) => { const s = performance.now(); const r = await fn(); return [label, Math.round(performance.now() - s), r]; };
    const csrf = document.querySelector('input[name=_csrf]').value;
    const body = new URLSearchParams({ _csrf: csrf, title: 'Timing probe for the tanker queue idea',
      abstractText: 'Households book a tanker and a scheduler orders the queue by shortest job first so small orders are never starved by large ones.',
      problem: '', tags: 'water, booking, queue, scheduling',
      domainId: document.querySelector('#domainId').options[1].value, typeId: document.querySelector('#typeId').options[1].value });
    const post = await t('POST /app/check', () => fetch('/app/check', { method: 'POST', body, redirect: 'manual' }).then(r => r.type + ' ' + r.status));
    const hist = await fetch('/app/history').then(r => r.text());
    const id = (hist.match(/result\\?id=(\\d+)/) || [])[1];
    const ev = await t('GET /app/evaluating', () => fetch('/app/evaluating?id=' + id).then(r => r.status));
    const res = await t('GET /app/result', () => fetch('/app/result?id=' + id).then(r => r.status));
    const res2 = await t('GET /app/result again', () => fetch('/app/result?id=' + id).then(r => r.status));
    const home = await t('GET /', () => fetch('/').then(r => r.status));
    const peek = await t('GET /peek', () => fetch('/peek?q=library+management+system+with+fines').then(r => r.status));
    return [post, ev, res, res2, home, peek, id];
  })()`);

  // page-level: when does evaluating.js run and when does it navigate?
  await b.goto(BASE + '/app/check?title=Timing+probe+two');
  await b.eval(`
    document.querySelector('#abstractText').value = 'Households book a tanker and a scheduler orders the queue by shortest job first so small orders are never starved by large ones.';
    document.querySelector('#tags').value = 'water, booking, queue, scheduling';
    const d = document.querySelector('#domainId'); d.value = d.options[1].value; const t = document.querySelector('#typeId'); t.value = t.options[1].value;`);
  const marks = [];
  await b.eval(`document.querySelector('#abstractText').form.submit()`);
  const t0 = Date.now();
  for (let i = 0; i < 100; i++) {
    await b.sleep(100);
    let st;
    try { st = await b.eval(`({ path: location.pathname, ready: document.readyState, stages: [...document.querySelectorAll('#stages .stage')].map(s => s.className.replace('stage','').trim() || '-').join(','), t: Math.round(performance.now()) })`); } catch { st = { path: 'navigating' }; }
    marks.push(`${Date.now() - t0}ms ${st.path} ${st.ready || ''} [${st.stages || ''}] page-t=${st.t ?? ''}`);
    if (/\/app\/result/.test(st.path)) break;
  }
  report.timeline = marks.filter((m, i) => i % 3 === 0 || /result/.test(m));
} catch (e) { report.failure = String(e.stack || e); }
console.log(JSON.stringify(report, null, 1));
await b.close();
process.exit(0);
