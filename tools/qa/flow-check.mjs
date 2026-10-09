import { launch } from './cdp.mjs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const out = path.join(path.dirname(new URL(import.meta.url).pathname.replace(/^\//, '')), 'shots');
const b = await launch({ width: 1440, height: 900, port: 9334 });
const report = {};
const BASE = 'http://localhost:8080';
async function login(email) {
  await b.goto(BASE + '/login');
  await b.eval(`document.querySelector('input[name=email]').value=${JSON.stringify(email)}; document.querySelector('input[name=password]').value='Origin@2026'; document.querySelector('form[action$="/login"], form').submit()`);
  await b.sleep(1200);
}
try {
  await login('sandesh@origin.edu');
  report.afterLoginUrl = await b.eval('location.pathname');

  // type on the home page, run the full check: the typed sentence must reach the form
  await b.goto(BASE + '/');
  await b.type('#idea', 'Water tanker booking queue that schedules tankers like an operating system', 4);
  await b.click('#stake-form button[type=submit]');
  await b.sleep(1200);
  report.formUrl = await b.eval('location.pathname + location.search.slice(0,40)');
  report.prefilled = await b.eval(`document.querySelector('#title')?.value`);
  await b.shot(path.join(out, 'flow-1-form.png'));

  // complete the form
  await b.eval(`
    document.querySelector('#abstractText').value = 'Households book a private water tanker and a scheduler orders the queue by shortest job first with aging, so small orders are not starved. A semaphore keeps two households from claiming the same tanker slot and a trigger records every delivery.';
    document.querySelector('#problem').value = 'Households in Kathmandu wait days for private water tankers with no idea of their queue position.';
    document.querySelector('#tags').value = 'water, booking, queue, scheduling';
    const d = document.querySelector('#domainId'); d.value = [...d.options].find(o => /Environment/.test(o.text)).value;
    const t = document.querySelector('#typeId'); t.value = [...t.options].find(o => /Web/.test(o.text)).value;
  `);
  const t0 = Date.now();
  await b.eval(`document.querySelector('#abstractText').form.submit()`);
  await b.sleep(700);
  report.evalUrl = await b.eval('location.pathname');
  report.evalStages = await b.eval(`[...document.querySelectorAll('#stages .stage')].map(s => s.className + ' | ' + s.querySelector('.stage-label').textContent + ' | ' + s.querySelector('.stage-count').textContent)`);
  await b.shot(path.join(out, 'flow-2-evaluating.png'));
  await b.sleep(900);
  report.evalStagesLater = await b.eval(`[...document.querySelectorAll('#stages .stage')].map(s => s.className)`);
  // wait for the result page
  for (let i = 0; i < 30; i++) { if ((await b.eval('location.pathname')).includes('/app/result')) break; await b.sleep(200); }
  report.evaluationSeconds = ((Date.now() - t0) / 1000).toFixed(1);
  report.resultUrl = await b.eval('location.pathname');
  await b.sleep(900);
  report.result = await b.eval(`({ pct: document.querySelector('.verdict .pct')?.textContent, label: document.querySelector('.verdict .label')?.textContent,
    fix: document.querySelector('.fix')?.textContent, cover: document.querySelector('.coverline')?.textContent,
    stories: document.querySelectorAll('.story').length })`);
  await b.shot(path.join(out, 'flow-3-result.png'), { full: true });

  await b.goto(BASE + '/app/ideas');
  report.ideas = await b.eval(`({ first: document.querySelector('.idea h3')?.textContent, twist: document.querySelector('.idea-twist')?.textContent,
    proof: document.querySelector('.idea-proof')?.textContent, count: document.querySelectorAll('.idea').length,
    topicOptions: document.querySelectorAll('#topic option').length })`);
  await b.shot(path.join(out, 'flow-4-ideas.png'));
  await b.goto(BASE + '/app/ideas?topic=' + (await b.eval(`[...document.querySelectorAll('#topic option')].find(o => /Deadlock/.test(o.text)).value`)));
  report.ideasByTopic = await b.eval(`[...document.querySelectorAll('.idea h3')].map(h => h.textContent)`);
  report.overflowX = await b.eval(`document.documentElement.scrollWidth > document.documentElement.clientWidth`);
} catch (e) { report.failure = String(e.stack || e); }
report.consoleErrors = b.errors;
console.log(JSON.stringify(report, null, 1));
await b.close();
process.exit(0);
