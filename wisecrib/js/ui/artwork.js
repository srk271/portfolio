// Deterministic cover artwork: category gradient + one of four patterns (by hash of the id) + category glyph.
import { raw } from '../dom.js';
import { hashString } from '../format.js';
import { getCategory } from '../data/categories.js';
import { glyphPath } from './icons.js';

let counter = 0;

function patternDef(kind, id, h) {
  switch (kind) {
    case 0:
      return `<pattern id="${id}p" width="18" height="18" patternUnits="userSpaceOnUse"><circle cx="9" cy="9" r="2.2" fill="#fff" fill-opacity=".22"/></pattern>`;
    case 1:
      return `<pattern id="${id}p" width="22" height="22" patternUnits="userSpaceOnUse" patternTransform="rotate(${30 + (h % 40)})"><rect width="7" height="22" fill="#fff" fill-opacity=".12"/></pattern>`;
    case 3:
      return `<pattern id="${id}p" width="24" height="24" patternUnits="userSpaceOnUse"><path d="M24 0H0V24" fill="none" stroke="#fff" stroke-opacity=".18" stroke-width="1"/></pattern>`;
    default:
      return '';
  }
}

export function coverArt(article, { variant = 'card' } = {}) {
  const cat = getCategory(article.category);
  const h = hashString(article.id);
  const id = `wa${(counter += 1).toString(36)}`;
  const square = variant === 'thumb';
  const W = square ? 160 : 320;
  const H = square ? 160 : 180;
  const [c1, c2] = cat.gradient;
  const angle = h % 360;
  const kind = h % 4;

  const rings = kind === 2
    ? Array.from({ length: 7 }, (_, i) => `<circle cx="${(h >> 3) % W}" cy="${(h >> 7) % H}" r="${24 + i * 26}" fill="none" stroke="#fff" stroke-opacity="${0.2 - i * 0.02}" stroke-width="1.5"/>`).join('')
    : `<rect width="${W}" height="${H}" fill="url(#${id}p)"/>`;

  const blobX = square ? 30 + (h % 40) : 40 + (h % 120);
  const blobY = square ? 120 - ((h >> 5) % 30) : 150 - ((h >> 5) % 40);
  const size = square ? 76 : 92;
  const gx = square ? W / 2 : W - 84 - ((h >> 9) % 40);
  const gy = square ? H / 2 : H / 2 - 4;
  const scale = size / 24;

  return raw(
    `<svg class="art" viewBox="0 0 ${W} ${H}" preserveAspectRatio="xMidYMid slice" aria-hidden="true" focusable="false">` +
      '<defs>' +
      `<linearGradient id="${id}g" gradientTransform="rotate(${angle} .5 .5)"><stop offset="0" stop-color="${c1}"/><stop offset="1" stop-color="${c2}"/></linearGradient>` +
      `<linearGradient id="${id}s" x1="0" y1="0" x2="0" y2="1"><stop offset=".45" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".22"/></linearGradient>` +
      patternDef(kind, id, h) +
      '</defs>' +
      `<rect width="${W}" height="${H}" fill="url(#${id}g)"/>` +
      rings +
      `<circle cx="${blobX}" cy="${blobY}" r="${square ? 46 : 70}" fill="#fff" fill-opacity=".09"/>` +
      `<circle cx="${gx}" cy="${gy}" r="${size * 0.78}" fill="#fff" fill-opacity=".12"/>` +
      `<g transform="translate(${gx - size / 2} ${gy - size / 2}) scale(${scale})" fill="none" stroke="#fff" stroke-width="${(1.35).toFixed(2)}" stroke-linecap="round" stroke-linejoin="round" stroke-opacity=".95">${glyphPath(cat.glyph)}</g>` +
      `<rect width="${W}" height="${H}" fill="url(#${id}s)"/>` +
      '</svg>'
  );
}

// Small solid artwork for vouchers / avatars.
export function gradientStyle([c1, c2], angle = 135) {
  return `background: linear-gradient(${angle}deg, ${c1}, ${c2});`;
}
