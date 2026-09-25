// Demo only — real enforcement (device/account checks, server-side timers, fraud scoring) belongs on the server.
//
// Referral: every user gets a code. A code entered at onboarding is "pending" and pays out once this
// device completes N verified reads (standing in for the referred friend's own activity).
import { update, getState } from '../store.js';
import { ECONOMY } from '../config.js';
import { randomCode } from '../format.js';
import { addEntry } from './wallet.js';

const prefix = ECONOMY.referral.codePrefix;
const escaped = prefix.replace(/[.*+?^${}()|[\]\\-]/g, '\\$&');
// Accepts 4-6 characters after the prefix (own codes are 4; partner/demo codes may be longer).
export const CODE_RE = new RegExp(`^${escaped}[A-Z0-9]{4,6}$`);

export function generateCode(avoid = null) {
  let code;
  do code = `${prefix}${randomCode(4)}`;
  while (code === avoid);
  return code;
}

export function normalizeCode(input) {
  return String(input || '').trim().toUpperCase().replace(/\s+/g, '');
}

export function validateCode(input, ownCode = null) {
  const code = normalizeCode(input);
  if (!code) return { ok: true, code: null };
  if (!CODE_RE.test(code)) return { ok: false, code, error: `Codes look like ${prefix}7K2Q (letters and numbers).` };
  if (ownCode && code === ownCode) return { ok: false, code, error: `That's your own code. Enter a friend's code instead.` };
  return { ok: true, code };
}

export function progress(state = getState()) {
  const required = ECONOMY.referral.requiredVerifiedReads;
  const r = state.referral;
  const done = r.status === 'paid' ? required : Math.min(required, Math.max(0, state.stats.verifiedReads - (r.verifiedReadsAtEntry || 0)));
  return { status: r.status, done, required, reward: ECONOMY.referral.reward, code: r.enteredCode };
}

// Draft helper: pays the referral reward once the condition is met.
export function maybePayReferral(s, now = Date.now()) {
  const r = s.referral;
  if (r.status !== 'pending') return false;
  const done = s.stats.verifiedReads - (r.verifiedReadsAtEntry || 0);
  if (done < ECONOMY.referral.requiredVerifiedReads) return false;
  addEntry(s, { type: 'referral', amount: ECONOMY.referral.reward, title: `Referral reward · code ${r.enteredCode}`, ts: now });
  r.status = 'paid';
  r.paidAt = now;
  return true;
}

export function shareText(code = getState().referral.code) {
  return `Join me on WiSeCRiB (demo): read the news, pass a quick quiz, earn rewards. Use my code ${code}.`;
}

export async function share() {
  const text = shareText();
  let method = 'manual';
  try {
    if (navigator.share) {
      await navigator.share({ title: 'WiSeCRiB', text });
      method = 'share';
    } else if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      method = 'clipboard';
    }
  } catch (err) {
    if (err?.name === 'AbortError') return { method: 'cancelled', text };
    method = 'manual';
  }
  update((s) => {
    s.referral.shares += 1;
  });
  return { method, text };
}
