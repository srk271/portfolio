// Partners & investors: positioning, launch model, unit-economics calculator, milestones, this-device analytics.
import { html, mount, delegate, disposer } from '../dom.js';
import { getState } from '../store.js';
import { APP, UNIT_ECONOMICS_DEFAULTS } from '../config.js';
import { PUBLISHERS, BRAND_PARTNERS, REVENUE_STREAMS, MILESTONES } from '../data/partners.js';
import { fmtRupeesCompact, fmtCompactNumber, fmtPercent, fmtRupees, fmtNumber, fmtCoins } from '../format.js';
import { icon, crownIcon, logoMark } from '../ui/icons.js';
import { computeUnitEconomics } from '../services/economics.js';
import { deriveStats } from '../services/stats.js';

// Percent inputs are shown as 0-100 and stored as fractions.
const FIELDS = [
  { key: 'mau', label: 'Monthly active users', min: 1000, max: 10000000, step: 1000, testid: 'partners-mau' },
  { key: 'readsPerUserPerDay', label: 'Verified reads per user per day', min: 0, max: 20, step: 0.5 },
  { key: 'adImpressionsPerRead', label: 'Ad impressions per read', min: 0, max: 10, step: 0.5 },
  { key: 'ecpmRupees', label: 'Ad eCPM (₹ per 1,000)', min: 0, max: 500, step: 1 },
  { key: 'payoutPerVerifiedReadRupees', label: 'Reward cost per verified read (₹)', min: 0, max: 5, step: 0.01 },
  { key: 'brandFundedShare', label: 'Brand-funded share of rewards (%)', min: 0, max: 100, step: 5, percent: true },
  { key: 'premiumConversion', label: 'Premium conversion (%)', min: 0, max: 100, step: 0.5, percent: true },
  { key: 'premiumPriceRupees', label: 'Premium price (₹/month)', min: 0, max: 999, step: 1 }
];

let assumptions = { ...UNIT_ECONOMICS_DEFAULTS };

const displayValue = (f, v) => (f.percent ? +(v * 100).toFixed(2) : v);

function outputsTemplate(o) {
  const neg = o.grossMargin < 0;
  return html`<dl class="kv econ-out">
    <div class="kv-row"><dt>Monthly verified reads</dt><dd class="num" data-testid="partners-reads">${fmtCompactNumber(o.monthlyReads)}</dd></div>
    <div class="kv-row"><dt>Ad impressions (free users only)</dt><dd class="num">${fmtCompactNumber(o.impressions)}</dd></div>
    <div class="kv-row"><dt>Ad revenue</dt><dd class="num">${fmtRupeesCompact(o.adRevenue)}</dd></div>
    <div class="kv-row"><dt>Premium revenue</dt><dd class="num">${fmtRupeesCompact(o.premiumRevenue)}</dd></div>
    <div class="kv-row"><dt>Reward cost (after brand funding)</dt><dd class="num neg">−${fmtRupeesCompact(o.rewardCost)}</dd></div>
    <div class="kv-row is-total"><dt>Gross margin / month</dt><dd class="num ${neg ? 'neg' : 'pos'}" data-testid="partners-margin">${fmtRupeesCompact(o.grossMargin)} · ${fmtPercent(o.marginPct)}</dd></div>
    <div class="kv-row"><dt>Margin per user / month</dt><dd class="num">${fmtRupees(o.perUser)}</dd></div>
    <div class="kv-row"><dt>Annualised revenue</dt><dd class="num">${fmtRupeesCompact(o.annualRevenue)}</dd></div>
  </dl>`;
}

function template(state) {
  const o = computeUnitEconomics(assumptions);
  const st = deriveStats(state);
  const maxSource = Math.max(1, ...st.bySource.map((x) => x.amount));
  const t = APP.planTargets;
  return html`<div class="page partners">
    <section class="partners-hero card card-ink">
      <div class="row">${logoMark({ size: 40 })}<span class="h-sub on-ink">For partners & investors</span></div>
      <h1 class="partners-title">${APP.tagline}</h1>
      <p class="on-ink-muted">A current-affairs reader for India's exam aspirants (UPSC and State PSC, SSC, Banking, Railways) and curious young readers. Verified reading and quick quizzes earn small, capped rewards funded by ads and brand partners.</p>
    </section>

    <section class="stack-sm">
      <h2 class="h-section">Launch model</h2>
      <div class="model-grid">
        ${REVENUE_STREAMS.launch.map((r) => html`<article class="card card-pad model-card"><span class="boost-ico ${r.key === 'premium' ? 'is-gold' : 'is-brand'}">${r.key === 'premium' ? crownIcon() : icon(r.icon)}</span><div class="strong">${r.title}</div><p class="muted small">${r.body}</p></article>`)}
      </div>
      <div class="h-sub mt-2">Later</div>
      <div class="chip-wrap">${REVENUE_STREAMS.later.map((l) => html`<span class="chip">${l}</span>`)}</div>
    </section>

    <section class="card card-pad stack" aria-labelledby="calc-title">
      <div class="row-between"><h2 class="h-section" id="calc-title">Unit-economics calculator</h2><button type="button" class="btn btn-ghost btn-sm" data-action="reset" data-testid="partners-reset">${icon('reset')}Reset</button></div>
      <div class="h-sub">Assumptions</div>
      <form class="econ-grid" data-testid="partners-form" novalidate>
        ${FIELDS.map((f) => html`<label class="field econ-field"><span class="field-label">${f.label}</span>
          <input class="input num" type="number" inputmode="decimal" name="${f.key}" min="${f.min}" max="${f.max}" step="${f.step}" value="${displayValue(f, assumptions[f.key])}" ${f.testid ? html`data-testid="${f.testid}"` : ''}></label>`)}
      </form>
      <div class="h-sub">Monthly outputs</div>
      <div data-outputs>${outputsTemplate(o)}</div>
      <p class="muted xsmall">Assumptions, not forecasts; daily caps, fraud losses and payment costs are not modelled. Premium users see no ads, so ad reads exclude them.</p>
      <p class="card-note is-info">${icon('timeline')}<span>Plan targets: ${fmtCompactNumber(t.usersYear1)} users and $${fmtNumber(t.revenueYear1Usd)} revenue in year 1; $${fmtNumber(t.marketCapUsd / 1e6)}M valuation within 2 years.</span></p>
    </section>

    <section class="stack-sm">
      <h2 class="h-section">Milestones</h2>
      <ol class="timeline card card-pad">${MILESTONES.map((m) => html`<li><span class="tl-when">${m.when}</span><span class="tl-title">${m.title}</span><span class="tl-body">${m.body}</span></li>`)}</ol>
    </section>

    <section class="stack-sm">
      <h2 class="h-section">Content and brand partners</h2>
      <div class="card list">${PUBLISHERS.map((p) => html`<div class="list-row"><span class="list-row-main"><span class="list-row-title">${p.name}</span><span class="list-row-sub">${p.focus}</span></span><span class="tag ${p.licenseType === 'original' ? 'tag-exam' : 'tag-partner'}">${p.licenseType}</span></div>`)}</div>
      <div class="chip-wrap">${BRAND_PARTNERS.map((b) => html`<span class="chip" title="${b.role}">${b.name}</span>`)}</div>
      <p class="muted xsmall">All publisher and brand names are fictional placeholders. Every article carries source and licence fields.</p>
    </section>

    <section class="card card-pad stack" data-testid="device-analytics">
      <h2 class="h-section">This device (sample analytics)</h2>
      <div class="tiles tiles-3">
        <div class="tile"><div class="tile-value">${st.articlesRead}</div><div class="tile-label">Articles opened</div></div>
        <div class="tile"><div class="tile-value">${st.verifiedReads}</div><div class="tile-label">Verified reads</div></div>
        <div class="tile"><div class="tile-value">${st.accuracy === null ? '—' : fmtPercent(st.accuracy)}</div><div class="tile-label">Quiz accuracy</div></div>
        <div class="tile"><div class="tile-value">${fmtNumber(st.adImpressions)}</div><div class="tile-label">Ad impressions (placeholder)</div></div>
        <div class="tile"><div class="tile-value">${st.rejected}</div><div class="tile-label">Rewards blocked by rules</div></div>
        <div class="tile"><div class="tile-value">${st.redemptions}</div><div class="tile-label">Redemptions</div></div>
      </div>
      <div class="h-sub">Coins by source</div>
      ${st.bySource.length
        ? html`<div class="bars">${st.bySource.map((x) => html`<div class="bar-row"><span class="bar-label" title="${x.label}">${x.label}</span><span class="bar-track"><span class="bar-fill" style="width:${((x.amount / maxSource) * 100).toFixed(1)}%"></span></span><span class="bar-val">${fmtCoins(x.amount)}</span></div>`)}</div>`
        : html`<p class="muted small">No coins earned on this device yet.</p>`}
      <p class="muted xsmall">Computed locally from this browser's demo data. A real product would report privacy-safe aggregates only.</p>
    </section>
  </div>`;
}

export function render(container) {
  const dispose = disposer();
  mount(container, template(getState()));
  const out = container.querySelector('[data-outputs]');
  const recompute = () => mount(out, outputsTemplate(computeUnitEconomics(assumptions)));

  dispose.add(delegate(container, 'input', '.econ-grid input', (e, input) => {
    const f = FIELDS.find((x) => x.key === input.name);
    if (!f) return;
    const raw = Number(input.value);
    if (input.value === '' || !Number.isFinite(raw)) return;
    const clamped = Math.min(f.max, Math.max(f.min, raw));
    assumptions = { ...assumptions, [f.key]: f.percent ? clamped / 100 : clamped };
    recompute();
  }));
  dispose.add(delegate(container, 'click', '[data-action="reset"]', () => {
    assumptions = { ...UNIT_ECONOMICS_DEFAULTS };
    container.querySelectorAll('.econ-grid input').forEach((input) => {
      const f = FIELDS.find((x) => x.key === input.name);
      input.value = String(displayValue(f, assumptions[f.key]));
    });
    recompute();
  }));
  return dispose;
}
