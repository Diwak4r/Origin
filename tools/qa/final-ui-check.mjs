// Example links, evaluation timing, reduced motion, keyboard use, and a rapid-typing burst.
import { launch } from './cdp.mjs';
const BASE = 'http://localhost:8080';
const b = await launch({ width: 1440, height: 900, port: 9337 });
const report = {};
async function login(email) {
  await b.goto(BASE + '/login');
  await b.eval(`document.querySelector('input[name=email]').value=${JSON.stringify(email)}; document.querySelector('input[name=password]').value='Origin@2026'; document.querySelector('input[name=email]').form.submit()`);
  await b.sleep(1200);
}
async function submitCheck(title) {
  await b.goto(BASE + '/app/check?title=' + encodeURIComponent(title));
  await b.eval(`
    document.querySelector('#abstractText').value = 'Households book a tanker and a scheduler orders the queue by shortest job first, so small orders are never starved by large ones in the same area.';
    document.querySelector('#tags').value = 'water, booking, queue, scheduling';
    const d = document.querySelector('#domainId'); d.value = [...d.options].find(o => /Environment/.test(o.text)).value;
    const t = document.querySelector('#typeId'); t.value = [...t.options].find(o => /Web/.test(o.text)).value;`);
  await b.eval(`document.querySelector('#abstractText').form.submit()`);
}
async function waitPath(re, ms = 12000) {
  const t0 = Date.now();
  while (Date.now() - t0 < ms) { if (re.test(await b.eval('location.pathname'))) return Date.now() - t0; await b.sleep(50); }
  return -1;
}
try {
  // 1. example links on the home page
  await b.goto(BASE + '/');
  await b.eval(`document.querySelector('a.example').click()`);
  await b.sleep(1000);
  report.exampleClick = await b.eval(`({ value: document.getElementById('idea').value.slice(0, 40), hot: document.querySelectorAll('.plot.is-hot').length, label: document.getElementById('ro-label').textContent, stayedOnHome: location.pathname === '/' })`);

  // 2. rapid typing makes one request, not one per key
  await b.goto(BASE + '/');
  await b.eval(`window.__peeks = 0; const of = window.fetch; window.fetch = function(u, o) { if (String(u).includes('/peek')) window.__peeks++; return of.apply(this, arguments); }`);
  await b.type('#idea', 'Hospital appointment booking with SMS reminders for patients', 5);
  await b.sleep(1200);
  report.peekCallsForOneBurst = await b.eval('window.__peeks');

  // 3. keyboard: a plot can be reached and activated without a mouse
  report.plotsAreButtons = await b.eval(`[...document.querySelectorAll('.plot')].every(p => p.tagName === 'BUTTON')`);
  await b.eval(`document.querySelector('.plot').focus()`);
  await b.send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13, text: '\r' });
  await b.send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13 });
  await b.sleep(400);
  report.keyboardSelect = await b.eval(`document.querySelector('.plot.sel')?.dataset.name || null`);

  // 4. evaluation timing, normal motion
  await login('sandesh@origin.edu');
  await submitCheck('Tanker queue that schedules like an operating system A');
  const onEval = await waitPath(/\/app\/evaluating/);
  const t1 = Date.now();
  const toResult = await waitPath(/\/app\/result/);
  report.evaluationScreenMs = toResult;
  report.reachedEvaluating = onEval >= 0;

  // 5. reduced motion goes straight to the result
  await b.send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'reduce' }] });
  const t2 = Date.now();
  await submitCheck('Tanker queue that schedules like an operating system B');
  const direct = await waitPath(/\/app\/result/, 8000);
  report.reducedMotionMsToResult = direct < 0 ? 'never' : Date.now() - t2;
} catch (e) { report.failure = String(e.stack || e); }
report.consoleErrors = b.errors;
console.log(JSON.stringify(report, null, 1));
await b.close();
process.exit(0);
