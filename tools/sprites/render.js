const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const [,, model, outPrefix, count, size, elev, yaw] = process.argv;
  const browser = await chromium.launch({ args: ['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--ignore-gpu-blocklist'] });
  const page = await browser.newPage();
  page.on('console', m => console.log('console:', m.text()));
  page.on('pageerror', e => console.log('pageerror:', e.message));
  await page.goto('http://localhost:8765/render.html');
  await page.waitForFunction('window.ready === true', null, {timeout: 20000});
  const n = parseInt(count);
  const angles = Array.from({length: n}, (_, i) => i * 360 / n);
  const urls = await page.evaluate(async ([m, a, s, e, y]) => window.renderModel(m, a, s, e, y), ['/models/' + model, angles, parseInt(size), parseFloat(elev), parseFloat(yaw || '0')]);
  urls.forEach((u, i) => fs.writeFileSync(`${outPrefix}_${String(i).padStart(2,'0')}.png`, Buffer.from(u.split(',')[1], 'base64')));
  console.log('wrote', urls.length);
  await browser.close();
})();
