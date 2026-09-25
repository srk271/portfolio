// Ad placeholders. Never part of reward logic: rendering an ad never earns coins.
// Premium users get '' (ad-free). Impressions are counted in one batched store update.
import { html } from '../dom.js';
import { getState } from '../store.js';
import { recordAdImpressions } from '../services/profile.js';

let pending = 0;
let scheduled = false;

function flush() {
  scheduled = false;
  const n = pending;
  pending = 0;
  recordAdImpressions(n);
}

export function renderAdSlot({ placement = 'feed' } = {}) {
  if (getState().premium.active) return '';
  pending += 1;
  if (!scheduled) {
    scheduled = true;
    setTimeout(flush, 0);
  }
  return html`<aside class="ad-slot" data-testid="ad-slot" data-placement="${placement}" aria-label="Advertisement placeholder">
    <span class="ad-slot-label">Ad · placeholder for Google AdMob</span>
    <span class="ad-slot-art" aria-hidden="true"></span>
    <span class="ad-slot-lines" aria-hidden="true"><span></span><span></span><span></span></span>
  </aside>`;
}
