// One-off dev script: renders icons/icon.svg and icons/icon-maskable.svg to the PNG icons via Playwright.
// Not loaded by the app and not precached. Run from the repo root with Playwright installed somewhere, e.g.:
//   NODE_PATH=/path/to/node_modules node wisecrib/tools/make-icons.mjs
import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const require = createRequire(import.meta.url);
const { chromium } = require('playwright'); // require() honours NODE_PATH; ESM import does not

const iconsDir = join(dirname(fileURLToPath(import.meta.url)), '..', 'icons');
const svg = (name) => readFileSync(join(iconsDir, name), 'utf8');

const jobs = [
  { src: 'icon.svg', out: 'icon-192.png', size: 192, transparent: true },
  { src: 'icon.svg', out: 'icon-512.png', size: 512, transparent: true },
  { src: 'icon-maskable.svg', out: 'icon-maskable-192.png', size: 192, transparent: false },
  { src: 'icon-maskable.svg', out: 'icon-maskable-512.png', size: 512, transparent: false },
  { src: 'icon-maskable.svg', out: 'apple-touch-icon.png', size: 180, transparent: false }
];

const browser = await chromium.launch();
const page = await browser.newPage();
for (const job of jobs) {
  await page.setViewportSize({ width: job.size, height: job.size });
  const data = Buffer.from(svg(job.src)).toString('base64');
  await page.setContent(`<!doctype html><html><body style="margin:0;background:transparent"><img src="data:image/svg+xml;base64,${data}" width="${job.size}" height="${job.size}" style="display:block"></body></html>`);
  await page.waitForFunction(() => document.images[0]?.complete);
  await page.screenshot({ path: join(iconsDir, job.out), omitBackground: job.transparent, clip: { x: 0, y: 0, width: job.size, height: job.size } });
  console.log('wrote', job.out);
}
await browser.close();
