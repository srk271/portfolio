// Inline SVG icons. Wrapped in raw() here, at the icon module boundary only.
import { raw } from '../dom.js';

const STROKE = {
  back: '<path d="M15 18l-6-6 6-6"/>',
  chevron: '<path d="M9 6l6 6-6 6"/>',
  close: '<path d="M18 6L6 18M6 6l12 12"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5"/>',
  check: '<path d="M20 6L9 17l-5-5"/>',
  checkCircle: '<circle cx="12" cy="12" r="9"/><path d="M8 12.5l2.8 2.8L16.5 9.5"/>',
  circle: '<circle cx="12" cy="12" r="9"/>',
  x: '<circle cx="12" cy="12" r="9"/><path d="M15 9l-6 6M9 9l6 6"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 16v-4.5M12 8h.01"/>',
  alert: '<path d="M12 3l9.5 17H2.5L12 3z"/><path d="M12 10v4M12 17.5h.01"/>',
  lock: '<rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V7a4 4 0 018 0v4"/>',
  share: '<circle cx="18" cy="5" r="2.6"/><circle cx="6" cy="12" r="2.6"/><circle cx="18" cy="19" r="2.6"/><path d="M8.4 13.3l7.2 4.3M15.6 6.4l-7.2 4.3"/>',
  copy: '<rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15V6a2 2 0 012-2h9"/>',
  external: '<path d="M14 4h6v6M20 4l-9 9"/><path d="M18 14v5a1 1 0 01-1 1H5a1 1 0 01-1-1V7a1 1 0 011-1h5"/>',
  flame: '<path d="M12 3c.8 3 4.5 5 4.5 9.5a4.5 4.5 0 01-9 0c0-1.8.8-3.2 1.8-4.2.4 1.4 1.3 2.3 2.2 2.4-.8-2.8.1-5.5.5-7.7z"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  gift: '<rect x="3.5" y="8" width="17" height="4" rx="1"/><path d="M12 8v13M5.5 12v7.5a1.5 1.5 0 001.5 1.5h10a1.5 1.5 0 001.5-1.5V12"/><path d="M12 8S10.8 3.5 8.2 3.5a2.2 2.2 0 000 4.5H12zM12 8s1.2-4.5 3.8-4.5a2.2 2.2 0 010 4.5H12z"/>',
  users: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0113 0"/><path d="M16 4.6a3.5 3.5 0 010 6.8M21.5 20a6.5 6.5 0 00-4-6"/>',
  shield: '<path d="M12 3l8 3v6c0 4.6-3.4 8.3-8 9-4.6-.7-8-4.4-8-9V6l8-3z"/><path d="M9 12l2 2 4-4"/>',
  play: '<circle cx="12" cy="12" r="9"/><path d="M10 8.5v7l6-3.5-6-3.5z"/>',
  sliders: '<path d="M4 6h9M17 6h3M4 12h3M11 12h9M4 18h11M19 18h1"/><circle cx="15" cy="6" r="2"/><circle cx="9" cy="12" r="2"/><circle cx="17" cy="18" r="2"/>',
  sparkle: '<path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3z"/><path d="M19 15.5l.7 1.8 1.8.7-1.8.7-.7 1.8-.7-1.8-1.8-.7 1.8-.7.7-1.8z"/>',
  trophy: '<path d="M8 4h8v5a4 4 0 01-8 0V4z"/><path d="M8 6H5v1.5A3.5 3.5 0 008.5 11M16 6h3v1.5A3.5 3.5 0 0115.5 11M12 13v4M8.5 20.5h7M9.5 17h5"/>',
  chart: '<path d="M4 20V11M10 20V5M16 20v-6M21 20H3"/>',
  download: '<path d="M12 4v11M7 10l5 5 5-5M5 20h14"/>',
  reset: '<path d="M4 4v5h5"/><path d="M4.6 14a8 8 0 102-7.9L4 9"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2.5v2M12 19.5v2M4.6 4.6l1.4 1.4M18 18l1.4 1.4M2.5 12h2M19.5 12h2M4.6 19.4L6 18M18 6l1.4-1.4"/>',
  moon: '<path d="M20 14.5A8 8 0 019.5 4 8 8 0 1020 14.5z"/>',
  heart: '<path d="M12 20s-7.5-4.4-7.5-10A4.2 4.2 0 0112 7.3 4.2 4.2 0 0119.5 10c0 5.6-7.5 10-7.5 10z"/>',
  phone: '<rect x="7" y="2.5" width="10" height="19" rx="2.2"/><path d="M11 18.5h2"/>',
  book: '<path d="M4 5.5A2.5 2.5 0 016.5 3H20v15H6.5A2.5 2.5 0 004 20.5v-15z"/><path d="M4 20.5A2.5 2.5 0 016.5 18H20v3H6.5"/>',
  target: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1.2"/>',
  newspaper: '<path d="M4 5h12v14a1.5 1.5 0 01-1.5 1.5H5.5A1.5 1.5 0 014 19V5z"/><path d="M16 9h3.5v9.5a2 2 0 01-2 2M7 9h6M7 12.5h6M7 16h4"/>',
  wallet: '<path d="M4 7.5A2.5 2.5 0 016.5 5H18v3"/><rect x="3.5" y="7.5" width="17" height="12.5" rx="2.5"/><path d="M16 13.8h1.5"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4.5 20.5a7.5 7.5 0 0115 0"/>',
  megaphone: '<path d="M4 10v4a1 1 0 001 1h2l7 4V5L7 9H5a1 1 0 00-1 1z"/><path d="M18 9.5a3.5 3.5 0 010 5"/>',
  store: '<path d="M4 9l1.5-5h13L20 9M4 9h16M4 9a2.7 2.7 0 005.3 0A2.7 2.7 0 0014.7 9 2.7 2.7 0 0020 9M5.5 11.5V20h13v-8.5M10 20v-5h4v5"/>',
  rupee: '<path d="M7 5h10M7 9.5h10M7 5h3a4.5 4.5 0 010 9H7l7.5 6"/>',
  bolt: '<path d="M13 2.5L5 13.5h6l-1 8 8-11h-6l1-8z"/>',
  train: '<rect x="6" y="3" width="12" height="13" rx="3"/><path d="M6 10h12M9 20l-2 1.5M15 20l2 1.5M9.5 13h.01M14.5 13h.01"/>',
  install: '<rect x="6" y="2.5" width="12" height="19" rx="2.2"/><path d="M12 7v7M9 11l3 3 3-3"/>',
  mail: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3.5 6.5l8.5 6 8.5-6"/>',
  eye: '<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="3"/>',
  calendar: '<rect x="3.5" y="5" width="17" height="15.5" rx="2"/><path d="M3.5 10h17M8 3v4M16 3v4"/>',
  timeline: '<circle cx="6" cy="6" r="2"/><circle cx="6" cy="18" r="2"/><path d="M6 8v8M10 6h10M10 18h10M10 12h7"/><circle cx="6" cy="12" r="1"/>',
  // Category glyphs
  pillar: '<path d="M3 9.5L12 4l9 5.5M5 10v8M9.7 10v8M14.3 10v8M19 10v8M3 21h18M4 18h16"/>',
  globe: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c3 3.3 3 14.7 0 18M12 3c-3 3.3-3 14.7 0 18"/>',
  atom: '<circle cx="12" cy="12" r="1.6"/><ellipse cx="12" cy="12" rx="9.5" ry="3.8"/><ellipse cx="12" cy="12" rx="9.5" ry="3.8" transform="rotate(60 12 12)"/><ellipse cx="12" cy="12" rx="9.5" ry="3.8" transform="rotate(120 12 12)"/>',
  leaf: '<path d="M5 19.5C5 10.5 11 5 20 5c0 9-5.5 15-14 15"/><path d="M5 19.5c3-5 6.5-8.5 10.5-10.5"/>',
  pulse: '<path d="M3 12h4l2.2-5.5 4.2 11 2.3-5.5H21"/>',
  medal: '<circle cx="12" cy="15" r="5.5"/><path d="M8.6 10.8L6 3h4.2L12 8l1.8-5H18l-2.6 7.8"/><path d="M12 12.5l.9 1.8 2 .3-1.4 1.4.3 2-1.8-.9-1.8.9.3-2-1.4-1.4 2-.3.9-1.8z"/>',
  vase: '<path d="M9 3h6M10 3v2.5C7 6.8 5 9.6 5 13c0 4.4 3.1 8 7 8s7-3.6 7-8c0-3.4-2-6.2-5-7.5V3"/><path d="M5.6 11h12.8M6 15.5h12"/>'
};

const ALIAS = { economyChart: 'chart' };

export function icon(name, { size = null, cls = '', label = null, strokeWidth = 2 } = {}) {
  const body = STROKE[ALIAS[name] || name];
  if (!body) return raw('');
  const dims = size ? ` width="${size}" height="${size}"` : '';
  const a11y = label ? ` role="img" aria-label="${label.replace(/"/g, '&quot;')}"` : ' aria-hidden="true" focusable="false"';
  return raw(`<svg class="ico ${cls}" viewBox="0 0 24 24"${dims} fill="none" stroke="currentColor" stroke-width="${strokeWidth}" stroke-linecap="round" stroke-linejoin="round"${a11y}>${body}</svg>`);
}

export const glyphPath = (name) => STROKE[name] || STROKE.sparkle;

// Solid-colour coin (no gradients, so it renders anywhere, including inside hidden parents).
export function coinIcon({ cls = '', label = null } = {}) {
  const a11y = label ? ` role="img" aria-label="${label}"` : ' aria-hidden="true" focusable="false"';
  return raw(
    `<svg class="coin-ico ${cls}" viewBox="0 0 24 24"${a11y}>` +
      '<circle cx="12" cy="12.6" r="10.4" fill="#C98E00"/>' +
      '<circle cx="12" cy="11.6" r="10.4" fill="#F5B301"/>' +
      '<circle cx="12" cy="11.6" r="7.6" fill="none" stroke="#FFE08A" stroke-width="1.3"/>' +
      '<path d="M7.7 8.6l1.9 5.9 2.4-4.4 2.4 4.4 1.9-5.9" fill="none" stroke="#7A5200" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>' +
      '</svg>'
  );
}

// Brand mark: gold disc with an ink rim and a "W" cut.
export function logoMark({ cls = '', size = null } = {}) {
  const dims = size ? ` width="${size}" height="${size}"` : '';
  return raw(
    `<svg class="logo-mark ${cls}" viewBox="0 0 64 64"${dims} aria-hidden="true" focusable="false">` +
      '<circle cx="32" cy="32" r="31" fill="#3230B0"/>' +
      '<circle cx="32" cy="32" r="25" fill="#F5B301"/>' +
      '<circle cx="32" cy="32" r="20.5" fill="none" stroke="#FFE08A" stroke-width="2"/>' +
      '<path d="M19.5 23.5l6 17.5 6.5-12.3 6.5 12.3 6-17.5" fill="none" stroke="#3230B0" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>' +
      '</svg>'
  );
}

export function wordmark() {
  return raw('<span class="wordmark">WiSe<b>CRiB</b></span>');
}

export function crownIcon({ cls = '' } = {}) {
  return raw(
    `<svg class="${cls}" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path fill="currentColor" d="M3 8.5l4.2 3.6L12 5l4.8 7.1L21 8.5 19.3 18H4.7L3 8.5z"/><rect x="4.7" y="19" width="14.6" height="2" rx="1" fill="currentColor"/></svg>`
  );
}
