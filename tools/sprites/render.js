const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const [,, model, outPrefix, count, size, elev, yaw, clip, nframes] = process.argv;
  const browser = await chromium.launch({ args: ['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--ignore-gpu-blocklist'] });
  const page = await browser.newPage();
  page.on('console', m => console.log('console:', m.text()));
  page.on('pageerror', e => console.log('pageerror:', e.message));
  await page.goto('http://localhost:8765/render.html');
  await page.waitForFunction('window.ready === true', null, {timeout: 20000});
  const n = parseInt(count);
  const half = process.env.HALF === '1';
  const angles = half ? Array.from({length: n / 2 + 1}, (_, i) => i * 360 / n) : Array.from({length: n}, (_, i) => i * 360 / n);
  const urls = await page.evaluate(async ([m, a, s, e, y, c, n]) => window.renderModel(m, a, s, e, y, c, n), ['/models/' + model, angles, parseInt(size), parseFloat(elev), parseFloat(yaw || '0'), clip || '', parseInt(nframes || '1')]);
  urls.forEach((u, i) => fs.writeFileSync(`${outPrefix}_${String(i).padStart(3,'0')}.png`, Buffer.from(u.split(',')[1], 'base64')));
  console.log('wrote', urls.length);
  await browser.close();
})();
