// Premium (simulated checkout): benefits from config, plan toggle, mock UPI payment, active state.
import { html, mount, delegate, disposer } from '../dom.js';
import { getState } from '../store.js';
import { ECONOMY } from '../config.js';
import { fmtRupees, fmtDate, fmtPercent } from '../format.js';
import { icon, crownIcon, coinIcon } from '../ui/icons.js';
import { openSheet } from '../ui/sheet.js';
import { toast } from '../ui/toast.js';
import { activate, priceFor, yearlySavingPct } from '../services/premium.js';

let plan = 'yearly';

function benefits() {
  const e = ECONOMY;
  const readP = Math.ceil(e.read.baseCoins * e.premium.multiplier);
  const quizP = Math.ceil(e.articleQuiz.passCoins * e.premium.multiplier);
  return [
    { icon: 'sparkle', title: `${e.premium.multiplier}× reading and quiz rewards`, body: `+${readP} per verified read and +${quizP} per article quiz (free: +${e.read.baseCoins} and +${e.articleQuiz.passCoins}).` },
    { icon: 'chart', title: `Higher daily cap: ${e.dailyCap.premium} coins`, body: `Free members are capped at ${e.dailyCap.free} activity coins a day.` },
    { icon: 'rupee', title: `0% redemption fee, ${fmtRupees(e.redemption.premium.minRupees, { decimals: 0 })} minimum`, body: `Free: ${fmtPercent(e.redemption.free.feeRate)} fee and ${fmtRupees(e.redemption.free.minRupees, { decimals: 0 })} minimum. Everyone can redeem.` },
    { icon: 'eye', title: 'Ad-free reading', body: 'No banner placements in the feed or reader.' },
    { icon: 'clock', title: `Daily mock test`, body: `${e.mockTest.questions} questions with a ${e.mockTest.minutes}-minute timer.` },
    { icon: 'mail', title: 'Weekly digest', body: 'A simulated weekly current-affairs newsletter.' },
    { icon: 'gift', title: `${e.dailyBonus.premiumMultiplier}× daily bonus`, body: `Up to +${e.dailyBonus.maxFree * e.dailyBonus.premiumMultiplier} a day (free: up to +${e.dailyBonus.maxFree}).` },
    { icon: 'shield', title: 'Streak protection', body: `One missed day forgiven, once every ${e.premium.streakProtectionEveryDays} days.` },
    { icon: 'store', title: 'Premium-only vouchers', body: 'Partner-funded rewards such as unlimited mock tests.' }
  ];
}

function template(state) {
  const p = state.premium;
  const maxMonthly = (ECONOMY.dailyCap.premium * 30) / ECONOMY.coinsPerRupee;
  return html`<div class="page premium">
    <section class="premium-hero card card-ink">
      <div class="premium-crown">${crownIcon()}</div>
      <h1 class="premium-title" data-large-title>WiSeCRiB Premium</h1>
      <p class="on-ink-muted">Better tools for serious aspirants, and fairer redemptions.</p>
      ${p.active ? html`<div class="premium-active" data-testid="premium-active"><span class="badge-premium on-ink-badge">${crownIcon()}Active</span><span>${p.plan === 'yearly' ? 'Yearly' : 'Monthly'} plan · renews ${fmtDate(p.renewsAt)}</span></div>` : ''}
    </section>
    <ul class="benefits card">
      ${benefits().map((b) => html`<li class="benefit"><span class="benefit-ico">${icon(b.icon)}</span><div><div class="benefit-title">${b.title}</div><div class="benefit-body">${b.body}</div></div></li>`)}
    </ul>
    ${p.active
      ? html`<div class="card card-pad stack-sm"><div class="strong">You're all set.</div><p class="muted small">Manage or cancel your plan in <a href="#/settings">Settings</a>. This checkout was simulated: nothing was charged.</p></div>`
      : html`<section class="stack">
        <div class="plan-toggle" role="radiogroup" aria-label="Choose a plan">
          <button type="button" class="plan ${plan === 'monthly' ? 'is-selected' : ''}" role="radio" aria-checked="${plan === 'monthly' ? 'true' : 'false'}" data-action="plan" data-plan="monthly" data-testid="plan-monthly">
            <span class="plan-name">Monthly</span><span class="plan-price">${fmtRupees(ECONOMY.premium.monthlyPrice, { decimals: 0 })}<small>/month</small></span>
          </button>
          <button type="button" class="plan ${plan === 'yearly' ? 'is-selected' : ''}" role="radio" aria-checked="${plan === 'yearly' ? 'true' : 'false'}" data-action="plan" data-plan="yearly" data-testid="plan-yearly">
            <span class="plan-save">Save ${yearlySavingPct()}%</span>
            <span class="plan-name">Yearly</span><span class="plan-price">${fmtRupees(ECONOMY.premium.yearlyPrice, { decimals: 0 })}<small>/year</small></span>
          </button>
        </div>
        <button type="button" class="btn btn-gold btn-block btn-lg" data-action="pay" data-testid="premium-pay">${icon('rupee')}Pay ${fmtRupees(priceFor(plan), { decimals: 0 })} with UPI (simulated)</button>
      </section>`}
    <p class="card-note" data-testid="premium-value-note">${coinIcon()}<span>Max activity earnings ≈ ${fmtRupees(maxMonthly, { decimals: 0 })}/month even on Premium. Premium is about tools and fairness, not income.</span></p>
    <p class="demo-note">${icon('info')}<span>Demo checkout: no payment is taken. Prices are illustrative.</span></p>
  </div>`;
}

export function render(container) {
  const dispose = disposer();
  const draw = () => mount(container, template(getState()));
  draw();
  dispose.add(delegate(container, 'click', '[data-action]', async (e, el) => {
    if (el.dataset.action === 'plan') {
      plan = el.dataset.plan;
      draw();
    } else if (el.dataset.action === 'pay') {
      const chosen = plan;
      const paid = await openSheet({
        title: 'Processing payment',
        testid: 'pay-sheet',
        dismissible: false,
        body: html`<div class="spinner" role="progressbar" aria-label="Processing"></div><p class="center muted">Simulated UPI payment of ${fmtRupees(priceFor(chosen), { decimals: 0 })}…</p>`,
        onMount(api) {
          setTimeout(() => api.close(true), 900);
        }
      });
      if (paid !== true) return;
      activate(chosen);
      toast({ message: 'Premium active · 1.5× rewards unlocked', kind: 'success', iconName: 'sparkle' });
      if (container.isConnected) {
        draw();
        container.scrollTop = 0;
      }
    }
  }));
  return dispose;
}
