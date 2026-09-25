// Wallet: balance hero, cap progress, boosts (daily bonus, referral, opt-in sponsored video), redemptions, ledger.
import { html, mount, delegate, disposer } from '../dom.js';
import { emit } from '../bus.js';
import { getState } from '../store.js';
import { ECONOMY } from '../config.js';
import { getVoucher } from '../data/vouchers.js';
import { fmtCoins, fmtRupees, coinsToRupees, fmtTime, fmtDayLabel, dateKey, fmtDate } from '../format.js';
import { icon, coinIcon, crownIcon } from '../ui/icons.js';
import { openSheet } from '../ui/sheet.js';
import { toast } from '../ui/toast.js';
import { capProgress, reasonText, SOURCE_LABELS, rewardedVideosLeft, completeRewardedVideo } from '../services/wallet.js';
import { dailyBonusAmount, canClaim, claim, streakDisplay } from '../services/bonus.js';
import { progress as referralProgress, share as shareReferral, shareText } from '../services/referral.js';

const TYPE_ICON = {
  welcome: 'sparkle', read: 'newspaper', quiz: 'target', sponsor_bonus: 'sparkle', daily_quiz: 'calendar', mock_test: 'clock',
  daily_bonus: 'gift', referral: 'users', rewarded_ad: 'play', demo_credit: 'bolt', redeem_cash: 'rupee', redeem_voucher: 'store', rejected: 'info'
};

const PAGE = 60;
let showAll = false;

export async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
    toast({ message: 'Copied to clipboard', kind: 'success', iconName: 'copy' });
    return true;
  } catch {
    toast({ message: `Copy this code: ${text}`, kind: 'info', iconName: 'copy', duration: 6000 });
    return false;
  }
}

function ledgerRow(e) {
  const tone = e.type === 'rejected' ? 'zero' : e.amount > 0 ? 'pos' : e.amount < 0 ? 'neg' : 'zero';
  const sign = e.amount > 0 ? '+' : e.amount < 0 ? '−' : '';
  return html`<li class="ledger-row" data-testid="ledger-row" data-type="${e.type}" data-reason="${e.reason || ''}">
    <span class="ledger-ico is-${tone} type-${e.type}">${icon(TYPE_ICON[e.type] || 'info')}</span>
    <div class="ledger-main">
      <div class="ledger-title">${e.title}</div>
      <div class="ledger-sub">${SOURCE_LABELS[e.type] || e.type} · ${fmtTime(e.ts)}</div>
      ${e.type === 'rejected' && e.meta?.articleTitle ? html`<div class="ledger-sub">${e.meta.articleTitle}</div>` : ''}
      ${e.reason ? html`<div class="ledger-reason">${icon('info')}<span>${reasonText(e.reason)}</span></div>` : ''}
    </div>
    <div class="ledger-amt is-${tone} num">${sign}${fmtCoins(Math.abs(e.amount))}</div>
  </li>`;
}

function ledgerTemplate(state) {
  if (!state.ledger.length) {
    return html`<div class="empty card" data-testid="ledger-empty">${icon('wallet')}<div class="empty-title">No activity yet</div><p>Verified reads and quizzes will show up here.</p></div>`;
  }
  const entries = [...state.ledger].reverse();
  const visible = showAll ? entries : entries.slice(0, PAGE);
  const groups = [];
  for (const e of visible) {
    const key = dateKey(new Date(e.ts));
    let g = groups[groups.length - 1];
    if (!g || g.key !== key) {
      g = { key, items: [] };
      groups.push(g);
    }
    g.items.push(e);
  }
  const today = dateKey();
  return html`<div class="ledger" data-testid="ledger">
    ${groups.map((g) => html`<section class="ledger-day">
      <h3 class="ledger-day-label">${fmtDayLabel(g.key, today)}</h3>
      <ul class="card ledger-list">${g.items.map(ledgerRow)}</ul>
    </section>`)}
    ${!showAll && entries.length > PAGE ? html`<button type="button" class="btn btn-secondary btn-block" data-action="show-all">Show all ${fmtCoins(entries.length)} entries</button>` : ''}
  </div>`;
}

function redemptionsTemplate(state) {
  if (!state.redemptions.length) {
    return html`<p class="muted small" data-testid="redemptions-empty">No redemptions yet. Everyone can redeem: free members from ${fmtRupees(ECONOMY.redemption.free.minRupees, { decimals: 0 })}, Premium from ${fmtRupees(ECONOMY.redemption.premium.minRupees, { decimals: 0 })}.</p>`;
  }
  return html`<ul class="card list" data-testid="redemptions">
    ${[...state.redemptions].reverse().map((r) => {
      const item = getVoucher(r.itemId);
      return html`<li class="redemption-row" data-testid="redemption-row" data-kind="${r.kind}">
        <span class="ledger-ico is-neg">${icon(r.kind === 'cash' ? 'rupee' : 'store')}</span>
        <div class="ledger-main">
          <div class="ledger-title">${r.kind === 'cash' ? `UPI payout · ${fmtRupees(r.netRupees)}` : `${item?.brand || 'Voucher'} · ${item?.title || ''}`}</div>
          <div class="ledger-sub">${fmtDate(r.ts)} · ${fmtCoins(r.coins)} coins${r.feeRupees ? ` · fee ${fmtRupees(r.feeRupees)}` : ' · no fee'} · <span class="tag tag-warning">Simulated</span></div>
          <div class="redemption-code"><code>${r.code}</code><button type="button" class="btn btn-ghost btn-sm" data-action="copy" data-code="${r.code}">${icon('copy')}Copy</button></div>
        </div>
      </li>`;
    })}
  </ul>`;
}

function boostsTemplate(state) {
  const claimable = canClaim(state);
  const amount = dailyBonusAmount(state);
  const streak = streakDisplay(state);
  const ref = referralProgress(state);
  const videosLeft = rewardedVideosLeft(state);
  const refStatus = ref.status === 'paid'
    ? html`<span class="tag tag-success" data-testid="referral-status" data-status="paid">${icon('check')}Paid · +${ref.reward} coins</span>`
    : ref.status === 'pending'
      ? html`<span class="tag tag-warning" data-testid="referral-status" data-status="pending">Pending · ${ref.done}/${ref.required} verified reads</span>`
      : html`<span class="tag" data-testid="referral-status" data-status="none">No code entered</span>`;
  return html`<div class="boosts">
    <article class="boost card" data-testid="daily-bonus-card">
      <span class="boost-ico is-gold">${icon('gift')}</span>
      <div class="boost-main">
        <div class="boost-title">Daily bonus</div>
        <div class="boost-sub">${claimable ? `+${amount} coins today` : 'Come back tomorrow'} · ${streak.current}-day streak${state.premium.active ? ' · 2× Premium' : ''}</div>
      </div>
      <button type="button" class="btn btn-sm ${claimable ? 'btn-gold' : 'btn-secondary'}" data-action="claim" data-testid="daily-bonus-claim" ${claimable ? '' : html`disabled`}>${claimable ? `Claim +${amount}` : 'Claimed today'}</button>
    </article>
    <article class="boost card boost--referral" data-testid="referral-card">
      <span class="boost-ico is-brand">${icon('users')}</span>
      <div class="boost-main">
        <div class="boost-title">Invite friends</div>
        <div class="boost-sub">Your code pays a friend +${ECONOMY.referral.reward} after their first ${ECONOMY.referral.requiredVerifiedReads} verified reads.</div>
        <div class="code-box mt-2"><span data-testid="own-referral-code">${state.referral.code || '—'}</span><button type="button" class="btn btn-primary btn-sm" data-action="share" data-testid="referral-share">${icon('share')}Share</button></div>
        <div class="boost-status"><span class="muted small">Code you entered${ref.code ? ` (${ref.code})` : ''}:</span> ${refStatus}</div>
      </div>
    </article>
    ${state.premium.active ? '' : html`<article class="boost card" data-testid="sponsored-video-card">
      <span class="boost-ico is-green">${icon('play')}</span>
      <div class="boost-main">
        <div class="boost-title">Sponsored video <span class="tag">Opt-in</span></div>
        <div class="boost-sub">+${ECONOMY.rewardedVideo.coins} coins · ${videosLeft} of ${ECONOMY.rewardedVideo.maxPerDay} left today · AdMob Rewarded format (simulated). Banner ads never pay coins.</div>
      </div>
      <button type="button" class="btn btn-sm btn-secondary" data-action="video" data-testid="sponsored-video" ${videosLeft ? '' : html`disabled`}>Watch</button>
    </article>`}
  </div>`;
}

function template(state) {
  const cap = capProgress(state);
  const rupees = coinsToRupees(state.wallet.balance);
  return html`<div class="page wallet">
    <section class="wallet-hero card card-ink">
      <div class="row-between"><span class="h-sub on-ink">Your balance</span>${state.premium.active ? html`<span class="badge-premium on-ink-badge">${crownIcon()}${ECONOMY.premium.multiplier}× rewards</span>` : html`<a class="tag tag-on-ink" href="#/premium">Free plan · Upgrade</a>`}</div>
      <div class="wallet-balance">${coinIcon()}<span class="num" data-testid="wallet-balance">${fmtCoins(state.wallet.balance)}</span><span class="wallet-unit">coins</span></div>
      <div class="wallet-rupees" data-testid="wallet-rupees">≈ ${fmtRupees(rupees)} · ${ECONOMY.coinsPerRupee} coins = ₹1</div>
      <div class="wallet-actions">
        <a class="btn btn-gold" href="#/redeem" data-testid="wallet-redeem">${icon('gift')}Redeem</a>
        <a class="btn btn-on-ink" href="#/how-rewards-work" data-testid="wallet-how">How rewards work</a>
      </div>
    </section>
    <section class="card card-pad cap-card" data-testid="cap-card">
      <div class="row-between"><span class="strong">Today's activity coins</span><span class="num strong" data-testid="wallet-cap-text">${cap.earned}/${cap.cap}</span></div>
      <div class="meter mt-2" role="progressbar" aria-label="Activity coins today" aria-valuemin="0" aria-valuemax="${cap.cap}" aria-valuenow="${cap.earned}"><span style="width:${(cap.ratio * 100).toFixed(1)}%"></span></div>
      <p class="muted small mt-2">Reads, quizzes and sponsored videos count toward the daily cap (${ECONOMY.dailyCap.free} free · ${ECONOMY.dailyCap.premium} Premium). Bonuses, referrals and brand-funded rewards don't.</p>
    </section>
    <h2 class="h-section">Boosts</h2>
    ${boostsTemplate(state)}
    <div class="section-head"><h2 class="h-section">Redemptions</h2><a class="small link-btn" href="#/redeem">Redeem</a></div>
    ${redemptionsTemplate(state)}
    <div class="section-head"><h2 class="h-section">Activity</h2><span class="muted small">Newest first · rejected rewards included</span></div>
    ${ledgerTemplate(state)}
  </div>`;
}

function watchVideo() {
  const seconds = ECONOMY.rewardedVideo.seconds;
  return openSheet({
    title: 'Sponsored video (simulated)',
    testid: 'video-sheet',
    body: html`<div class="video-mock" aria-hidden="true"><div class="video-mock-art">${icon('play')}</div><div class="video-mock-label">Sponsor message placeholder</div></div>
      <div class="meter is-brand"><span data-video-bar></span></div>
      <p class="muted small" data-video-text>Watch for ${seconds} seconds to earn +${ECONOMY.rewardedVideo.coins} coins. Closing early earns nothing.</p>`,
    actions: [{ label: 'Close', value: false, variant: 'btn-ghost', testid: 'video-close' }],
    onMount(api) {
      const bar = api.el.querySelector('[data-video-bar]');
      const text = api.el.querySelector('[data-video-text]');
      const start = performance.now();
      const id = setInterval(() => {
        const t = Math.min(1, (performance.now() - start) / (seconds * 1000));
        bar.style.width = `${t * 100}%`;
        if (t >= 1) {
          clearInterval(id);
          text.textContent = 'Done!';
          api.close(true);
        } else {
          text.textContent = `${Math.ceil(seconds * (1 - t))}s left · closing early earns nothing.`;
        }
      }, 100);
      const origClose = api.close;
      api.close = (v) => {
        clearInterval(id);
        origClose(v);
      };
    }
  });
}

export function render(container) {
  const dispose = disposer();
  showAll = false;
  const draw = () => {
    const top = container.scrollTop;
    mount(container, template(getState()));
    container.scrollTop = top;
  };
  draw();

  dispose.add(delegate(container, 'click', '[data-action]', async (e, el) => {
    const action = el.dataset.action;
    if (action === 'claim') {
      const from = el.getBoundingClientRect();
      const r = claim();
      draw();
      if (!r.already) emit('coins:awarded', { amount: r.amount, from, message: `Daily bonus · +${r.amount} coins` });
    } else if (action === 'share') {
      const r = await shareReferral();
      if (r.method === 'clipboard') toast({ message: 'Invite copied to clipboard', kind: 'success', iconName: 'copy' });
      else if (r.method === 'manual') {
        openSheet({
          title: 'Share your code',
          body: html`<p class="muted">Copy this message and send it to a friend:</p><div class="code-box share-text">${shareText()}</div>`,
          actions: [{ label: 'Done', variant: 'btn-primary' }]
        });
      }
    } else if (action === 'copy') {
      copyText(el.dataset.code);
    } else if (action === 'show-all') {
      showAll = true;
      draw();
    } else if (action === 'video') {
      const from = el.getBoundingClientRect();
      const watched = await watchVideo();
      if (!watched) return;
      const r = completeRewardedVideo();
      if (container.isConnected) draw();
      if (r.coins > 0) emit('coins:awarded', { amount: r.coins, from, message: `Sponsored video · +${r.coins} coins` });
      else emit('reward:rejected', { reason: r.reason });
    }
  }));
  return dispose;
}
