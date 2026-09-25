// Demo only — real enforcement (device/account checks, server-side timers, fraud scoring) belongs on the server.
//
// Simulated one-time payout verification. The demo OTP is returned to the calling view and kept only in
// that view's memory; it is never persisted. A real app would send an SMS and verify server-side (plus KYC).
import { update, getState } from '../store.js';
import { APP } from '../config.js';
import { randomDigits } from '../format.js';

export const UPI_RE = /^[\w.-]{2,}@[a-z]{2,}$/;
export const PHONE_RE = /^[6-9]\d{9}$/;

export const isVerified = (state = getState()) => Boolean(state.payout.verified);

export function validateDetails(upiInput, phoneInput) {
  const upiId = String(upiInput || '').trim().toLowerCase();
  const phone = String(phoneInput || '').replace(/[\s-]/g, '').replace(/^\+?91(?=\d{10}$)/, '');
  const errors = {};
  if (!UPI_RE.test(upiId)) errors.upi = 'Enter a UPI ID like name@bank.';
  if (!PHONE_RE.test(phone)) errors.phone = 'Enter a 10-digit mobile number.';
  return { ok: Object.keys(errors).length === 0, errors, upiId, phone };
}

export function startVerification(upiInput, phoneInput) {
  const v = validateDetails(upiInput, phoneInput);
  if (!v.ok) return { ok: false, errors: v.errors };
  return {
    ok: true,
    session: { upiId: v.upiId, phone: v.phone, otp: randomDigits(APP.otp.digits), attemptsLeft: APP.otp.maxAttempts, sentAt: Date.now() }
  };
}

export function maskPhone(phone) {
  return `••••••${String(phone).slice(-4)}`;
}

export function confirmOtp(session, code) {
  if (!session) return { ok: false, error: 'Send an OTP first.' };
  if (session.attemptsLeft <= 0) return { ok: false, error: 'Too many attempts. Send a new OTP.', attemptsLeft: 0 };
  const entered = String(code || '').trim();
  if (entered !== session.otp) {
    session.attemptsLeft -= 1;
    return {
      ok: false,
      attemptsLeft: session.attemptsLeft,
      error: session.attemptsLeft > 0 ? `That code doesn't match. ${session.attemptsLeft} attempt${session.attemptsLeft === 1 ? '' : 's'} left.` : 'Too many attempts. Send a new OTP.'
    };
  }
  update((s) => {
    s.payout = { verified: true, upiId: session.upiId, phoneMasked: maskPhone(session.phone), verifiedAt: Date.now() };
  });
  return { ok: true };
}
