// Settings: theme, motion, newsletter, premium status, install, export, demo tools, reset, about.
import { html, mount, delegate, disposer } from '../dom.js';
import { on, emit } from '../bus.js';
import { getState, storageAvailable } from '../store.js';
import { APP, ECONOMY } from '../config.js';
import { fmtCoins, fmtDate, dateKey } from '../format.js';
import { icon, crownIcon } from '../ui/icons.js';
import { confirmSheet } from '../ui/sheet.js';
import { toast } from '../ui/toast.js';
import { setTheme, setReduceMotion, setNewsletter, exportData, resetDemoData } from '../services/profile.js';
import { addDemoCredit } from '../services/wallet.js';
import { cancel as cancelPremium } from '../services/premium.js';
import { canInstall, promptInstall, isStandalone } from '../pwa.js';

function installRow() {
  if (isStandalone()) return html`<p class="muted small">Installed: you're using the app version.</p>`;
  if (canInstall()) return html`<button type="button" class="btn btn-secondary btn-block" data-action="install" data-testid="install-app">${icon('install')}Install WiSeCRiB</button>`;
  return html`<p class="muted small">To install, use your browser menu: <b>Add to Home screen</b> (Android/iOS) or the install icon in the address bar (desktop).</p>`;
}

function template(state) {
  const theme = state.settings.theme;
  const motionOn = state.settings.reduceMotion === 'on';
  const p = state.premium;
  return html`<div class="page settings">
    <header class="page-head"><h1 class="h-display" data-large-title>Settings</h1></header>
    ${storageAvailable() ? '' : html`<p class="card-note is-warning" data-testid="storage-warning">${icon('alert')}<span>Storage unavailable — progress won't persist after you close this tab.</span></p>`}
    <section class="settings-group card card-pad stack">
      <h2 class="h-sub">Appearance</h2>
      <div class="field"><span class="field-label" id="theme-label">Theme</span>
        <div class="segmented" role="group" aria-labelledby="theme-label">
          ${['system', 'light', 'dark'].map((t) => html`<button type="button" aria-pressed="${theme === t ? 'true' : 'false'}" data-action="theme" data-theme-value="${t}" data-testid="theme-${t}">${t === 'system' ? 'System' : t === 'light' ? html`${icon('sun', { size: 16 })} Light` : html`${icon('moon', { size: 16 })} Dark`}</button>`)}
        </div>
      </div>
      <div class="setting-row"><div class="grow"><div class="strong">Reduce motion</div><div class="muted small">No coin particles; instant counters.</div></div>
        <button type="button" class="switch" role="switch" aria-checked="${motionOn ? 'true' : 'false'}" aria-label="Reduce motion" data-action="motion" data-testid="reduce-motion"></button></div>
    </section>
    <section class="settings-group card card-pad stack">
      <h2 class="h-sub">Premium</h2>
      ${p.active
        ? html`<div class="row-between"><div><div class="strong">${crownIcon({ cls: 'inline-crown' })} ${p.plan === 'yearly' ? 'Yearly' : 'Monthly'} plan</div><div class="muted small">Renews ${fmtDate(p.renewsAt)} · simulated</div></div><button type="button" class="btn btn-danger-soft btn-sm" data-action="cancel-premium" data-testid="cancel-premium">Cancel</button></div>`
        : html`<div class="row-between"><div><div class="strong">Free plan</div><div class="muted small">${ECONOMY.premium.multiplier}× rewards, 0% fee, mock tests, no ads.</div></div><a class="btn btn-primary btn-sm" href="#/premium">See Premium</a></div>`}
      <div class="setting-row"><div class="grow"><div class="strong">Weekly digest</div><div class="muted small">${p.active ? 'Simulated weekly newsletter.' : 'Premium feature · simulated weekly newsletter.'}</div></div>
        <button type="button" class="switch" role="switch" aria-checked="${state.settings.newsletter ? 'true' : 'false'}" aria-label="Weekly digest" data-action="newsletter" data-testid="newsletter" ${p.active ? '' : html`disabled`}></button></div>
    </section>
    <section class="settings-group card card-pad stack">
      <h2 class="h-sub">App</h2>
      <div data-install>${installRow()}</div>
      <button type="button" class="btn btn-secondary btn-block" data-action="export" data-testid="export-data">${icon('download')}Export my data (JSON)</button>
    </section>
    <section class="settings-group card card-pad stack demo-tools">
      <h2 class="h-sub">Demo tools</h2>
      <p class="muted small">For trying redemptions quickly. Demo credit is labeled in the ledger and can't be earned in the real app.</p>
      <button type="button" class="btn btn-secondary btn-block" data-action="demo-credit" data-testid="demo-credit">${icon('bolt')}Add ${fmtCoins(ECONOMY.demoCredit)} demo coins</button>
      <button type="button" class="btn btn-danger-soft btn-block" data-action="reset" data-testid="reset-data">${icon('reset')}Reset demo data</button>
    </section>
    <nav class="card list" aria-label="About">
      <a class="list-row" href="#/how-rewards-work">${icon('shield')}<span class="list-row-main"><span class="list-row-title">How rewards work</span></span>${icon('chevron', { cls: 'chev' })}</a>
      <a class="list-row" href="#/partners">${icon('chart')}<span class="list-row-main"><span class="list-row-title">For partners & investors</span></span>${icon('chevron', { cls: 'chev' })}</a>
    </nav>
    <p class="about muted xsmall center" data-testid="about">${APP.name} ${APP.version} · cache ${APP.cacheVersion} · data schema v${APP.schemaVersion}${APP.devOverridesActive ? ' · dev overrides active' : ''}<br>Front-end prototype. Content, rewards, payments and payouts are simulated.</p>
  </div>`;
}

export function render(container, ctx) {
  const dispose = disposer();
  const draw = () => {
    const top = container.scrollTop;
    mount(container, template(getState()));
    container.scrollTop = top;
  };
  draw();
  dispose.add(on('install:available', () => {
    const slot = container.querySelector('[data-install]');
    if (slot) mount(slot, installRow());
  }));

  dispose.add(delegate(container, 'click', '[data-action]', async (e, el) => {
    const action = el.dataset.action;
    const s = getState();
    if (action === 'theme') {
      setTheme(el.dataset.themeValue);
      draw();
    } else if (action === 'motion') {
      setReduceMotion(s.settings.reduceMotion !== 'on');
      draw();
    } else if (action === 'newsletter') {
      setNewsletter(!s.settings.newsletter);
      draw();
      toast({ message: getState().settings.newsletter ? 'Weekly digest on (simulated)' : 'Weekly digest off', kind: 'success' });
    } else if (action === 'cancel-premium') {
      const ok = await confirmSheet({ title: 'Cancel Premium?', body: html`<p class="muted">You'll go back to the free plan now: ${ECONOMY.dailyCap.free}-coin daily cap, ${Math.round(ECONOMY.redemption.free.feeRate * 100)}% redemption fee and ads. Nothing is refunded because nothing was charged.</p>`, confirmLabel: 'Cancel Premium', cancelLabel: 'Keep Premium', variant: 'btn-danger' });
      if (!ok) return;
      cancelPremium();
      draw();
      toast({ message: 'Premium cancelled', kind: 'info' });
    } else if (action === 'install') {
      const outcome = await promptInstall();
      if (outcome === 'accepted') toast({ message: 'Installing WiSeCRiB…', kind: 'success' });
      const slot = container.querySelector('[data-install]');
      if (slot) mount(slot, installRow());
    } else if (action === 'export') {
      const blob = new Blob([exportData()], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `wisecrib-export-${dateKey()}.json`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      toast({ message: 'Export downloaded', kind: 'success', iconName: 'download' });
    } else if (action === 'demo-credit') {
      const from = el.getBoundingClientRect();
      addDemoCredit();
      emit('coins:awarded', { amount: ECONOMY.demoCredit, from, message: `+${fmtCoins(ECONOMY.demoCredit)} demo coins (not earnable)` });
    } else if (action === 'reset') {
      const ok = await confirmSheet({ title: 'Reset demo data?', body: html`<p class="muted">This clears your profile, coins, reads, quizzes and redemptions on this device. It can't be undone.</p>`, confirmLabel: 'Reset everything', variant: 'btn-danger' });
      if (!ok) return;
      resetDemoData();
      ctx.navigate('#/onboarding');
      toast({ message: 'Demo data cleared', kind: 'info' });
    }
  }));
  return dispose;
}
