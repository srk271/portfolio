// Coin particles fly from the source to the header pill (Web Animations API), then the pill pulses and
// the balance counts up. Reduced motion (system or setting): no particles, instant count, toast only.
import { html, mount } from '../dom.js';
import { coinIcon } from './icons.js';
import { holdBalance, releaseBalance, pulsePill } from './header.js';

export const reducedMotion = () => document.documentElement.dataset.motion === 'reduce';

const clamp = (n, min, max) => Math.min(max, Math.max(min, n));

export function burst({ from = null, amount = 1 } = {}) {
  const pill = document.getElementById('coin-pill');
  const pillRect = pill?.getBoundingClientRect();
  if (!pill || !pillRect || pillRect.width === 0 || reducedMotion() || typeof Element.prototype.animate !== 'function') {
    pulsePill();
    return Promise.resolve();
  }
  const rect = from instanceof Element ? from.getBoundingClientRect() : from;
  const vw = window.innerWidth;
  const vh = window.innerHeight;
  const sx = rect && rect.width ? clamp(rect.left + rect.width / 2, 24, vw - 24) : vw / 2;
  const sy = rect && rect.height ? clamp(rect.top + rect.height / 2, 80, vh - 80) : vh / 2;
  const tx = pillRect.left + 16;
  const ty = pillRect.top + pillRect.height / 2 - 11;

  holdBalance();
  const layer = document.createElement('div');
  layer.className = 'coin-layer';
  document.body.appendChild(layer);

  const animations = [];
  const label = document.createElement('div');
  label.className = 'coin-float';
  label.textContent = `+${amount}`;
  layer.appendChild(label);
  animations.push(
    label.animate(
      [
        { transform: `translate(${sx - 16}px, ${sy - 20}px) scale(.8)`, opacity: 0 },
        { transform: `translate(${sx - 16}px, ${sy - 44}px) scale(1.15)`, opacity: 1, offset: 0.3 },
        { transform: `translate(${sx - 16}px, ${sy - 70}px) scale(1)`, opacity: 0 }
      ],
      { duration: 1000, easing: 'ease-out', fill: 'forwards' }
    )
  );

  const count = clamp(8 + amount, 10, 14);
  for (let i = 0; i < count; i += 1) {
    const el = document.createElement('span');
    el.className = 'coin-particle';
    mount(el, html`${coinIcon()}`);
    layer.appendChild(el);
    const x0 = sx - 11 + (Math.random() - 0.5) * 70;
    const y0 = sy - 11 + (Math.random() - 0.5) * 36;
    const cx = (x0 + tx) / 2 + (Math.random() - 0.5) * 140;
    const cy = Math.min(y0, ty) - 50 - Math.random() * 90;
    const frames = [];
    const steps = 14;
    for (let k = 0; k <= steps; k += 1) {
      const t = k / steps;
      const x = (1 - t) ** 2 * x0 + 2 * (1 - t) * t * cx + t ** 2 * tx;
      const y = (1 - t) ** 2 * y0 + 2 * (1 - t) * t * cy + t ** 2 * ty;
      const scale = t < 0.15 ? 0.5 + t * 4 : 1.1 - t * 0.55;
      frames.push({ transform: `translate(${x}px, ${y}px) scale(${scale.toFixed(3)}) rotate(${Math.round(t * 360)}deg)`, opacity: t < 0.08 ? t * 12 : t > 0.92 ? (1 - t) * 10 : 1 });
    }
    animations.push(el.animate(frames, { duration: 700 + Math.random() * 260, delay: i * 38, easing: 'cubic-bezier(.4,0,.7,1)', fill: 'forwards' }));
  }

  let done = false;
  const finish = () => {
    if (done) return;
    done = true;
    layer.remove();
    pulsePill();
    releaseBalance();
  };
  const safety = setTimeout(finish, 2200);
  return Promise.all(animations.map((a) => a.finished.catch(() => null))).then(() => {
    clearTimeout(safety);
    finish();
  });
}
