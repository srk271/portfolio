import { ECONOMY } from './config.js';

const intFmt = new Intl.NumberFormat('en-IN');
const rupeeFmt2 = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const rupeeFmt0 = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 });
const DAY = 86400000;

export const fmtNumber = (n) => intFmt.format(Math.round(Number(n) || 0));
export const fmtCoins = (n) => intFmt.format(Math.round(Number(n) || 0));
export const coinsToRupees = (coins) => (Number(coins) || 0) / ECONOMY.coinsPerRupee;
export const rupeesToCoins = (rupees) => Math.round((Number(rupees) || 0) * ECONOMY.coinsPerRupee);
export const round2 = (n) => Math.round((Number(n) + Number.EPSILON) * 100) / 100;

export function fmtRupees(rupees, { decimals = 2 } = {}) {
  const value = Number(rupees) || 0;
  const sign = value < 0 ? '−' : '';
  const fmt = decimals === 0 ? rupeeFmt0 : rupeeFmt2;
  return `${sign}₹${fmt.format(Math.abs(value))}`;
}

// ₹5.24 L / ₹1.20 Cr style for the partners calculator.
export function fmtRupeesCompact(rupees) {
  const value = Number(rupees) || 0;
  const abs = Math.abs(value);
  const sign = value < 0 ? '−' : '';
  if (abs >= 1e7) return `${sign}₹${(abs / 1e7).toFixed(2)} Cr`;
  if (abs >= 1e5) return `${sign}₹${(abs / 1e5).toFixed(2)} L`;
  if (abs >= 1000) return `${sign}₹${intFmt.format(Math.round(abs))}`;
  return `${sign}₹${rupeeFmt2.format(abs)}`;
}

export function fmtCompactNumber(n) {
  const value = Number(n) || 0;
  const abs = Math.abs(value);
  if (abs >= 1e7) return `${(value / 1e7).toFixed(2)} Cr`;
  if (abs >= 1e5) return `${(value / 1e5).toFixed(2)} L`;
  return intFmt.format(Math.round(value));
}

export const fmtPercent = (fraction, digits = 0) => `${(Number(fraction) * 100).toFixed(digits)}%`;

export function fmtDuration(ms) {
  const totalSec = Math.max(0, Math.round((Number(ms) || 0) / 1000));
  const h = Math.floor(totalSec / 3600);
  const m = Math.floor((totalSec % 3600) / 60);
  const s = totalSec % 60;
  if (h) return `${h}h ${m}m`;
  if (m) return `${m}m ${String(s).padStart(2, '0')}s`;
  return `${s}s`;
}

export function fmtClock(ms) {
  const totalSec = Math.max(0, Math.floor((Number(ms) || 0) / 1000));
  const m = Math.floor(totalSec / 60);
  const s = totalSec % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
}

export function timeAgo(iso, now = Date.now()) {
  const then = new Date(iso).getTime();
  const diff = Math.max(0, now - then);
  const min = Math.floor(diff / 60000);
  if (min < 1) return 'just now';
  if (min < 60) return `${min}m ago`;
  const hours = Math.floor(min / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}d ago`;
  const weeks = Math.floor(days / 7);
  if (days < 30) return `${weeks}w ago`;
  return fmtDate(iso);
}

export function fmtDate(value, opts = { day: 'numeric', month: 'short', year: 'numeric' }) {
  return new Date(value).toLocaleDateString('en-IN', opts);
}

export function fmtTime(value) {
  return new Date(value).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' });
}

// Local-time YYYY-MM-DD, used for all daily gating (pitfall: never UTC).
export function dateKey(d = new Date()) {
  const date = d instanceof Date ? d : new Date(d);
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function keyToUtc(key) {
  const [y, m, d] = key.split('-').map(Number);
  return Date.UTC(y, m - 1, d);
}

export function daysBetween(fromKey, toKey) {
  return Math.round((keyToUtc(toKey) - keyToUtc(fromKey)) / DAY);
}

export function addDays(key, n) {
  const t = keyToUtc(key) + n * DAY;
  return new Date(t).toISOString().slice(0, 10);
}

export function fmtDayLabel(key, todayKey = dateKey()) {
  const diff = daysBetween(key, todayKey);
  if (diff === 0) return 'Today';
  if (diff === 1) return 'Yesterday';
  const [y, m, d] = key.split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' });
}

// FNV-1a 32-bit hash: deterministic artwork, avatars and daily quiz seeds.
export function hashString(str) {
  let h = 0x811c9dc5;
  const s = String(str);
  for (let i = 0; i < s.length; i += 1) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 0x01000193);
  }
  return h >>> 0;
}

export function mulberry32(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export function seededShuffle(list, seed) {
  const rand = mulberry32(typeof seed === 'number' ? seed : hashString(seed));
  const arr = [...list];
  for (let i = arr.length - 1; i > 0; i -= 1) {
    const j = Math.floor(rand() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}

const CODE_ALPHABET = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';

export function randomCode(length, alphabet = CODE_ALPHABET) {
  const bytes = new Uint32Array(length);
  crypto.getRandomValues(bytes);
  let out = '';
  for (let i = 0; i < length; i += 1) out += alphabet[bytes[i] % alphabet.length];
  return out;
}

export function uid(prefix = 'id') {
  return `${prefix}_${Date.now().toString(36)}${randomCode(4).toLowerCase()}`;
}

export function randomDigits(n) {
  return randomCode(n, '0123456789');
}

export const clamp = (n, min, max) => Math.min(max, Math.max(min, n));
export const plural = (n, one, many = `${one}s`) => `${fmtNumber(n)} ${Math.abs(n) === 1 ? one : many}`;
