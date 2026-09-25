// Demo only — real enforcement (device/account checks, server-side timers, fraud scoring) belongs on the server.
//
// Redemption quotes and simulated fulfilment. Everyone can redeem; the tier sets the minimum and fee.
// quote() always returns the full breakdown so the UI can show coins -> value -> fee -> receive before confirming.
import { update, getState } from '../store.js';
import { ECONOMY } from '../config.js';
import { VOUCHER_MAP } from '../data/vouchers.js';
import { round2, rupeesToCoins, coinsToRupees, randomCode, uid, fmtRupees } from '../format.js';
import { spend } from './wallet.js';

export function tierFor(state = getState()) {
  return state.premium.active ? ECONOMY.redemption.premium : ECONOMY.redemption.free;
}

export function isPartnerFunded(item) {
  return item.coins < item.faceValueRupees * ECONOMY.coinsPerRupee;
}

export function quote({ kind, rupees, itemId }, state = getState()) {
  const premium = Boolean(state.premium.active);
  const balance = state.wallet.balance;
  if (kind === 'cash') {
    const tier = tierFor(state);
    const value = Number.isFinite(Number(rupees)) ? Math.floor(Number(rupees)) : 0;
    const coins = rupeesToCoins(value);
    const feeRate = tier.feeRate;
    const feeRupees = round2(value * feeRate);
    const feeCoins = rupeesToCoins(feeRupees);
    const netRupees = round2(value - feeRupees);
    let reason = null;
    if (!(value >= tier.minRupees)) reason = 'below_min';
    else if (value > ECONOMY.redemption.maxCashRupees) reason = 'above_max';
    else if (coins > balance) reason = 'insufficient';
    else if (!state.payout.verified) reason = 'payout_unverified';
    return {
      kind, ok: reason === null, reason, premium, coins, valueRupees: value, feeRate, feeCoins, feeRupees, netRupees,
      minRupees: tier.minRupees, maxRupees: ECONOMY.redemption.maxCashRupees, balance
    };
  }
  const item = VOUCHER_MAP[itemId];
  if (!item) return { kind: 'voucher', ok: false, reason: 'not_found' };
  const feeRate = item.feeExempt ? ECONOMY.redemption.charityFeeRate : tierFor(state).feeRate;
  const feeCoins = Math.ceil(item.coins * feeRate);
  const coins = item.coins + feeCoins;
  let reason = null;
  if (item.premiumOnly && !premium) reason = 'premium_only';
  else if (coins > balance) reason = 'insufficient';
  return {
    kind: 'voucher', ok: reason === null, reason, premium, item, itemId, baseCoins: item.coins, coins,
    valueRupees: item.faceValueRupees, feeRate, feeCoins, feeRupees: coinsToRupees(feeCoins), netRupees: item.faceValueRupees,
    minRupees: 0, balance
  };
}

export function reasonMessage(q) {
  switch (q?.reason) {
    case 'below_min': return `Minimum payout is ${fmtRupees(q.minRupees, { decimals: 0 })}${q.premium ? '' : ` on the free plan (${fmtRupees(ECONOMY.redemption.premium.minRupees, { decimals: 0 })} with Premium)`}.`;
    case 'above_max': return `Maximum ${fmtRupees(q.maxRupees, { decimals: 0 })} per payout in this demo.`;
    case 'insufficient': return `Not enough coins. You have ${q.balance.toLocaleString('en-IN')} coins.`;
    case 'premium_only': return 'Premium members only.';
    case 'payout_unverified': return 'One-time verification needed before your first UPI payout.';
    case 'not_found': return 'This voucher is no longer available.';
    default: return '';
  }
}

export function redeemCash(rupees) {
  return update((s) => {
    const q = quote({ kind: 'cash', rupees }, s);
    if (!q.ok) return { ok: false, reason: q.reason, quote: q };
    const now = Date.now();
    const redemption = {
      id: uid('rd'), ts: now, kind: 'cash', coins: q.coins, valueRupees: q.valueRupees, feeRate: q.feeRate,
      feeCoins: q.feeCoins, feeRupees: q.feeRupees, netRupees: q.netRupees, code: `UPI-SIM-${randomCode(6)}`,
      upiId: s.payout.upiId, status: 'simulated'
    };
    spend(s, {
      type: 'redeem_cash', amount: q.coins,
      title: `UPI payout ${fmtRupees(q.netRupees)}${q.feeRupees ? ` (after ${fmtRupees(q.feeRupees)} fee)` : ''} · simulated`,
      meta: { redemptionId: redemption.id }
    });
    s.redemptions.push(redemption);
    return { ok: true, redemption, quote: q };
  });
}

export function redeemVoucher(itemId) {
  return update((s) => {
    const q = quote({ kind: 'voucher', itemId }, s);
    if (!q.ok) return { ok: false, reason: q.reason, quote: q };
    const now = Date.now();
    const redemption = {
      id: uid('rd'), ts: now, kind: 'voucher', itemId, coins: q.coins, valueRupees: q.valueRupees, feeRate: q.feeRate,
      feeCoins: q.feeCoins, feeRupees: q.feeRupees, netRupees: q.netRupees,
      code: `WSC-VCH-${randomCode(4)}-${randomCode(4)}`, status: 'simulated'
    };
    spend(s, { type: 'redeem_voucher', amount: q.coins, title: `${q.item.brand}: ${q.item.title} · simulated`, meta: { redemptionId: redemption.id, itemId } });
    s.redemptions.push(redemption);
    return { ok: true, redemption, quote: q };
  });
}
