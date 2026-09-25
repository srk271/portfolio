// Redeem: cash (simulated UPI) with a live fee breakdown and a one-time OTP gate; brand-funded vouchers.
import { html, mount, delegate, disposer } from '../dom.js';
import { getState } from '../store.js';
import { ECONOMY, APP } from '../config.js';
import { VOUCHERS } from '../data/vouchers.js';
import { fmtCoins, fmtRupees, fmtPercent, coinsToRupees } from '../format.js';
import { icon, coinIcon, crownIcon } from '../ui/icons.js';
import { gradientStyle } from '../ui/artwork.js';
import { openSheet } from '../ui/sheet.js';
import { toast } from '../ui/toast.js';
import { quote, redeemCash, redeemVoucher, reasonMessage, isPartnerFunded, tierFor } from '../services/redeem.js';
import { startVerification, confirmOtp } from '../services/payout.js';
import { copyText } from './wallet.js';

let tab = 'cash';
let amount = null;

const VOUCHER_ICON = { recharge: 'phone', data: 'bolt', learning: 'target', books: 'book', stationery: 'sliders', charity: 'heart' };

function feeLabel(q) {
  return `${fmtRupees(q.feeRupees)} (${fmtPercent(q.feeRate)})`;
}

function quoteTable(q, { testids = true } = {}) {
  const t = (id) => (testids ? html`data-testid="${id}"` : '');
  if (q.kind === 'voucher') {
    return html`<dl class="kv quote">
      <div class="kv-row"><dt>Voucher</dt><dd>${q.item.title}</dd></div>
      <div class="kv-row"><dt>Coin cost</dt><dd class="num">${fmtCoins(q.baseCoins)}</dd></div>
      <div class="kv-row"><dt>Fee (${fmtPercent(q.feeRate)})</dt><dd class="num" ${t('voucher-fee')}>${fmtCoins(q.feeCoins)} coins${q.feeCoins ? ` · ${fmtRupees(q.feeRupees)}` : ''}</dd></div>
      <div class="kv-row"><dt>Total coins</dt><dd class="num">${fmtCoins(q.coins)}</dd></div>
      <div class="kv-row is-total"><dt>You receive</dt><dd>${fmtRupees(q.netRupees, { decimals: 0 })} ${q.item.category === 'charity' ? 'donation' : 'voucher'}</dd></div>
    </dl>`;
  }
  return html`<dl class="kv quote">
    <div class="kv-row"><dt>Coins</dt><dd class="num" ${t('quote-coins')}>${fmtCoins(q.coins)}</dd></div>
    <div class="kv-row"><dt>Value</dt><dd class="num" ${t('quote-value')}>${fmtRupees(q.valueRupees)}</dd></div>
    <div class="kv-row"><dt>Fee</dt><dd class="num" ${t('quote-fee')}>${feeLabel(q)}</dd></div>
    <div class="kv-row is-total"><dt>You receive</dt><dd class="num" ${t('quote-net')}>${fmtRupees(q.netRupees)}</dd></div>
  </dl>`;
}

function tierBanner(state) {
  const { free, premium } = ECONOMY.redemption;
  return html`<div class="tier-banner ${state.premium.active ? 'is-premium' : ''}" data-testid="tier-banner">
    <div class="tier ${state.premium.active ? '' : 'is-current'}"><span class="tier-name">Free</span><span>min ${fmtRupees(free.minRupees, { decimals: 0 })} · ${fmtPercent(free.feeRate)} fee</span></div>
    <div class="tier ${state.premium.active ? 'is-current' : ''}"><span class="tier-name">${crownIcon()}Premium</span><span>min ${fmtRupees(premium.minRupees, { decimals: 0 })} · ${fmtPercent(premium.feeRate)} fee</span></div>
    ${state.premium.active ? '' : html`<a class="tier-upgrade" href="#/premium">Upgrade</a>`}
  </div>`;
}

function cashTemplate(state) {
  const tier = tierFor(state);
  const q = quote({ kind: 'cash', rupees: amount });
  const note = q.reason && q.reason !== 'payout_unverified' ? reasonMessage(q) : '';
  return html`<section class="stack" data-testid="cash-panel">
    <div class="balance-strip">${coinIcon()}<span>Available <b class="num">${fmtCoins(state.wallet.balance)}</b> coins ≈ ${fmtRupees(coinsToRupees(state.wallet.balance))}</span></div>
    <div class="field">
      <span class="field-label" id="amt-label">Payout amount</span>
      <div class="presets" role="group" aria-labelledby="amt-label">
        ${ECONOMY.redemption.cashPresetsRupees.map((r) => html`<button type="button" class="chip chip-lg" data-action="preset" data-value="${r}" data-testid="preset-${r}" aria-pressed="${amount === r ? 'true' : 'false'}">${fmtRupees(r, { decimals: 0 })}</button>`)}
      </div>
      <label class="input-affix mt-2"><span class="affix">₹</span><input class="input num" type="number" inputmode="numeric" min="${tier.minRupees}" max="${ECONOMY.redemption.maxCashRupees}" step="1" value="${amount ?? ''}" aria-label="Custom amount in rupees" data-testid="redeem-cash-amount"></label>
    </div>
    <div class="card card-pad" data-testid="cash-quote">${quoteTable(q)}</div>
    <p class="quote-note ${note ? 'is-error' : ''}" data-testid="quote-note" role="status">${note || (state.payout.verified ? `Pays to ${state.payout.upiId} (verified).` : 'First payout? You will verify your UPI ID and phone once (simulated OTP).')}</p>
    ${!state.premium.active && q.feeRupees > 0 ? html`<p class="card-note is-info">${crownIcon()}<span>Premium members pay 0% fee: you'd receive the full ${fmtRupees(q.valueRupees)}. <a href="#/premium">See Premium</a></span></p>` : ''}
    <button type="button" class="btn btn-primary btn-block btn-lg" data-action="confirm-cash" data-testid="redeem-confirm" ${q.reason && q.reason !== 'payout_unverified' ? html`disabled` : ''}>${icon('rupee')}Confirm UPI payout (simulated)</button>
  </section>`;
}

function voucherCard(v, state) {
  const q = quote({ kind: 'voucher', itemId: v.id });
  const locked = v.premiumOnly && !state.premium.active;
  const feeLine = v.feeExempt
    ? html`<span class="voucher-fee is-free">0% fee · goes in full</span>`
    : q.feeCoins
      ? html`<span class="voucher-fee" data-testid="voucher-fee-line">+${fmtPercent(q.feeRate)} fee · ${fmtCoins(q.feeCoins)} coins</span>`
      : html`<span class="voucher-fee is-free">0% fee${state.premium.active ? ' (Premium)' : ''}</span>`;
  return html`<article class="voucher card ${locked ? 'is-locked' : ''}" data-testid="voucher-${v.id}" data-locked="${locked ? 'true' : 'false'}">
    <div class="voucher-art" style="${gradientStyle(v.gradient)}">
      ${icon(VOUCHER_ICON[v.category] || 'gift')}
      <div class="voucher-badges">
        ${isPartnerFunded(v) ? html`<span class="tag tag-partner">Partner-funded</span>` : ''}
        ${v.premiumOnly ? html`<span class="tag tag-premium">${crownIcon()}Premium</span>` : ''}
      </div>
    </div>
    <div class="voucher-body">
      <div class="voucher-brand">${v.brand}</div>
      <div class="voucher-title">${v.title}</div>
      <div class="voucher-cost">${coinIcon()}<b class="num">${fmtCoins(v.coins)}</b><span class="muted">· worth ${fmtRupees(v.faceValueRupees, { decimals: 0 })}</span></div>
      ${feeLine}
      ${locked
        ? html`<a class="btn btn-sm btn-secondary btn-block" href="#/premium" data-testid="voucher-upgrade">${icon('lock')}Premium only</a>`
        : html`<button type="button" class="btn btn-sm btn-primary btn-block" data-action="voucher" data-id="${v.id}" ${q.reason === 'insufficient' ? html`disabled title="Not enough coins"` : ''}>Redeem</button>`}
    </div>
  </article>`;
}

function vouchersTemplate(state) {
  return html`<section class="stack" data-testid="voucher-panel">
    <p class="muted small">Vouchers are brand-funded: partners cover part of the value, so coins go further. ${state.premium.active ? 'Premium: 0% fee on everything.' : `Free plan: ${fmtPercent(ECONOMY.redemption.free.feeRate)} fee in coins, except charity.`}</p>
    <div class="voucher-grid">${VOUCHERS.map((v) => voucherCard(v, state))}</div>
  </section>`;
}

function template(state) {
  return html`<div class="page redeem">
    <header class="page-head"><h1 class="h-display" data-large-title>Redeem</h1><p class="muted">Everyone can redeem. See every coin, fee and rupee before you confirm.</p></header>
    ${tierBanner(state)}
    <div class="segmented" role="tablist" aria-label="Redemption type">
      <button type="button" role="tab" aria-selected="${tab === 'cash' ? 'true' : 'false'}" data-action="tab" data-tab="cash" data-testid="tab-cash">Cash (UPI)</button>
      <button type="button" role="tab" aria-selected="${tab === 'vouchers' ? 'true' : 'false'}" data-action="tab" data-tab="vouchers" data-testid="tab-vouchers">Vouchers</button>
    </div>
    <div data-panel>${tab === 'cash' ? cashTemplate(state) : vouchersTemplate(state)}</div>
    <p class="demo-note">${icon('info')}<span>Simulated: no money moves and codes are not real. <a href="#/how-rewards-work?from=redeem">How rewards work</a></span></p>
  </div>`;
}

// One-time payout verification. The demo OTP lives only in this closure (view memory).
function verifyPayout() {
  let session = null;
  return openSheet({
    title: 'Verify payout details',
    testid: 'otp-sheet',
    body: html`<p class="muted small">A one-time check before your first UPI payout. Simulated: no SMS is sent and nothing leaves this device.</p>
      <label class="field"><span class="field-label">UPI ID</span>
        <input class="input" name="upi" placeholder="name@bank" autocomplete="off" autocapitalize="none" spellcheck="false" data-testid="otp-upi">
        <span class="field-error" data-err="upi" role="alert"></span></label>
      <label class="field"><span class="field-label">Mobile number</span>
        <span class="input-affix"><span class="affix">+91</span><input class="input num" name="phone" inputmode="numeric" maxlength="10" placeholder="10-digit number" autocomplete="off" data-testid="otp-phone"></span>
        <span class="field-error" data-err="phone" role="alert"></span></label>
      <button type="button" class="btn btn-primary btn-block" data-testid="otp-send">Send OTP (simulated)</button>
      <div class="otp-step stack-sm" hidden>
        <div class="card-note is-info" data-testid="otp-banner">${icon('info')}<span>Demo OTP: <b class="otp-code" data-testid="otp-demo-code"></b> · in the real app this arrives by SMS.</span></div>
        <label class="field"><span class="field-label">Enter the ${APP.otp.digits}-digit OTP</span>
          <input class="input otp-input num" name="otp" inputmode="numeric" maxlength="6" autocomplete="one-time-code" data-testid="otp-input">
          <span class="field-error" data-err="otp" role="alert"></span></label>
        <button type="button" class="btn btn-gold btn-block" data-testid="otp-verify">Verify and continue</button>
      </div>`,
    onMount(api) {
      const $ = (sel) => api.el.querySelector(sel);
      const setErr = (key, msg) => {
        $(`[data-err="${key}"]`).textContent = msg || '';
      };
      $('[data-testid="otp-send"]').addEventListener('click', () => {
        const res = startVerification($('[name="upi"]').value, $('[name="phone"]').value);
        setErr('upi', res.errors?.upi);
        setErr('phone', res.errors?.phone);
        $('[name="upi"]').toggleAttribute('aria-invalid', Boolean(res.errors?.upi));
        $('[name="phone"]').toggleAttribute('aria-invalid', Boolean(res.errors?.phone));
        if (!res.ok) return;
        session = res.session;
        $('[data-testid="otp-demo-code"]').textContent = session.otp;
        $('.otp-step').hidden = false;
        $('[data-testid="otp-send"]').textContent = 'Resend OTP (simulated)';
        setErr('otp', '');
        $('[name="otp"]').value = '';
        $('[name="otp"]').focus();
      });
      const verify = () => {
        const res = confirmOtp(session, $('[name="otp"]').value);
        if (res.ok) {
          api.close(true);
          return;
        }
        setErr('otp', res.error);
        if (res.attemptsLeft === 0) {
          session = null;
          $('.otp-step').hidden = true;
        }
      };
      $('[data-testid="otp-verify"]').addEventListener('click', verify);
      $('[name="otp"]').addEventListener('keydown', (e) => {
        if (e.key === 'Enter') verify();
      });
    }
  }).then((v) => v === true);
}

function successBody({ code, lines, note }) {
  return html`<div class="success-mark">${icon('check')}</div>
    <div class="stack-sm center">${lines}</div>
    <div class="code-box" data-testid="redeem-success-code-box"><span data-testid="redeem-success-code">${code}</span><button type="button" class="btn btn-ghost btn-sm" data-copy="${code}">${icon('copy')}Copy</button></div>
    <p class="card-note is-warning">${icon('info')}<span>${note}</span></p>`;
}

function showSuccess(opts) {
  return openSheet({
    title: opts.title,
    testid: 'success-sheet',
    body: successBody(opts),
    actions: [{ label: 'Done', variant: 'btn-primary', testid: 'sheet-done' }],
    onMount(api) {
      api.el.querySelector('[data-copy]').addEventListener('click', (e) => copyText(e.currentTarget.dataset.copy));
    }
  });
}

export function render(container, ctx) {
  const dispose = disposer();
  if (ctx.query.tab === 'vouchers' || ctx.query.tab === 'cash') tab = ctx.query.tab;
  const state = getState();
  if (amount === null || amount < tierFor(state).minRupees) amount = tierFor(state).minRupees;

  const draw = () => {
    const top = container.scrollTop;
    mount(container, template(getState()));
    container.scrollTop = top;
  };

  const refreshQuote = () => {
    const s = getState();
    const q = quote({ kind: 'cash', rupees: amount });
    const panel = container.querySelector('[data-testid="cash-quote"]');
    if (!panel) return;
    mount(panel, quoteTable(q));
    const note = q.reason && q.reason !== 'payout_unverified' ? reasonMessage(q) : '';
    const noteEl = container.querySelector('[data-testid="quote-note"]');
    noteEl.textContent = note || (s.payout.verified ? `Pays to ${s.payout.upiId} (verified).` : 'First payout? You will verify your UPI ID and phone once (simulated OTP).');
    noteEl.classList.toggle('is-error', Boolean(note));
    container.querySelector('[data-testid="redeem-confirm"]').disabled = Boolean(note);
    container.querySelectorAll('[data-action="preset"]').forEach((b) => b.setAttribute('aria-pressed', String(Number(b.dataset.value) === amount)));
  };

  draw();

  dispose.add(delegate(container, 'input', '[data-testid="redeem-cash-amount"]', (e, input) => {
    amount = input.value === '' ? 0 : Math.floor(Number(input.value));
    refreshQuote();
  }));

  dispose.add(delegate(container, 'click', '[data-action]', async (e, el) => {
    const action = el.dataset.action;
    if (action === 'tab') {
      tab = el.dataset.tab;
      draw();
    } else if (action === 'preset') {
      amount = Number(el.dataset.value);
      container.querySelector('[data-testid="redeem-cash-amount"]').value = String(amount);
      refreshQuote();
    } else if (action === 'confirm-cash') {
      let q = quote({ kind: 'cash', rupees: amount });
      if (q.reason === 'payout_unverified') {
        const ok = await verifyPayout();
        if (!ok) return;
        toast({ message: 'Payout details verified', kind: 'success', iconName: 'shield' });
        q = quote({ kind: 'cash', rupees: amount });
      }
      if (!q.ok) {
        toast({ message: reasonMessage(q), kind: 'warn' });
        return;
      }
      const confirmed = await openSheet({
        title: 'Confirm UPI payout',
        testid: 'confirm-sheet',
        body: html`${quoteTable(q, { testids: false })}<p class="muted small">To <b>${getState().payout.upiId}</b> · ${fmtCoins(q.coins)} coins will leave your wallet.</p>`,
        actions: [
          { label: `Pay out ${fmtRupees(q.netRupees)} (simulated)`, value: true, variant: 'btn-primary', testid: 'sheet-confirm' },
          { label: 'Cancel', value: false, variant: 'btn-ghost', testid: 'sheet-cancel' }
        ]
      });
      if (confirmed !== true) return;
      const r = redeemCash(amount);
      if (!r.ok) {
        toast({ message: reasonMessage(r.quote), kind: 'warn' });
        return;
      }
      if (container.isConnected) draw();
      await showSuccess({
        title: 'Payout sent (simulated)',
        code: r.redemption.code,
        lines: html`<div class="success-amount">${fmtRupees(r.redemption.netRupees)}</div><div class="muted">to ${r.redemption.upiId}${r.redemption.feeRupees ? ` · fee ${fmtRupees(r.redemption.feeRupees)}` : ' · no fee'}</div>`,
        note: 'No money moves in this demo. A real payout would go through a licensed payment provider after KYC.'
      });
    } else if (action === 'voucher') {
      const q = quote({ kind: 'voucher', itemId: el.dataset.id });
      if (!q.ok) {
        toast({ message: reasonMessage(q), kind: 'warn' });
        return;
      }
      const confirmed = await openSheet({
        title: `Redeem ${q.item.title}`,
        testid: 'confirm-sheet',
        body: html`<p class="muted small">${q.item.brand} · ${q.item.description}</p>${quoteTable(q, { testids: false })}`,
        actions: [
          { label: `Redeem for ${fmtCoins(q.coins)} coins`, value: true, variant: 'btn-primary', testid: 'sheet-confirm' },
          { label: 'Cancel', value: false, variant: 'btn-ghost', testid: 'sheet-cancel' }
        ]
      });
      if (confirmed !== true) return;
      const r = redeemVoucher(q.itemId);
      if (!r.ok) {
        toast({ message: reasonMessage(r.quote), kind: 'warn' });
        return;
      }
      if (container.isConnected) draw();
      await showSuccess({
        title: q.item.category === 'charity' ? 'Donation made (simulated)' : 'Voucher ready (simulated)',
        code: r.redemption.code,
        lines: html`<div class="success-amount">${q.item.title}</div><div class="muted">${q.item.brand} · ${fmtCoins(r.redemption.coins)} coins</div>`,
        note: 'Demo voucher code: not redeemable anywhere. Real vouchers would come from the brand partner.'
      });
    }
  }));
  return dispose;
}
