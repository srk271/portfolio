// Demo only — real enforcement (device/account checks, server-side timers, fraud scoring) belongs on the server.
//
// Verified reading: a read counts once the reader has (1) reached the end of the article and
// (2) accumulated active dwell time of max(minDwellSeconds, dwellFraction x expected reading time).
// Dwell only accrues while the page is visible. The award path is a single atomic store update:
// once-per-article -> velocity limit -> daily cap -> ledger (reward or logged rejection).
import { update, getState } from '../store.js';
import { ECONOMY } from '../config.js';
import { getArticle } from '../data/articles.js';
import { addEntry, rollActivity, capFor } from './wallet.js';
import { updateStreakOnRead } from './bonus.js';
import { maybePayReferral } from './referral.js';

export function requiredDwellMs(article) {
  const { minDwellSeconds, dwellFraction, wpm } = ECONOMY.read;
  return Math.round(Math.max(minDwellSeconds * 1000, dwellFraction * (article.wordCount / wpm) * 60000));
}

export function readCoinsFor(state = getState()) {
  return Math.ceil(ECONOMY.read.baseCoins * (state.premium.active ? ECONOMY.premium.multiplier : 1));
}

export function readStatus(articleId, state = getState()) {
  return state.reads[articleId] || null;
}

export function markOpened(articleId) {
  if (!getArticle(articleId) || getState().stats.openedIds.includes(articleId)) return;
  update((s) => {
    if (!s.stats.openedIds.includes(articleId)) s.stats.openedIds.push(articleId);
  });
}

const TICK_MS = 250;

export function createReadTracker({ article, sentinelEl, scrollEl, onProgress, onVerified }) {
  const requiredMs = requiredDwellMs(article);
  let activeMs = 0;
  let reachedEnd = false;
  let verified = false;
  let destroyed = false;
  let lastTick = performance.now();
  let observer = null;

  const emitProgress = () => onProgress?.({ activeMs, requiredMs, reachedEnd, ratio: Math.min(1, activeMs / requiredMs) });

  const maybeVerify = () => {
    if (verified || destroyed || !reachedEnd || activeMs < requiredMs) return;
    verified = true;
    clearInterval(timer);
    onVerified?.({ dwellMs: Math.round(activeMs) });
  };

  const setReached = () => {
    if (reachedEnd) return;
    reachedEnd = true;
    emitProgress();
    maybeVerify();
  };

  const checkScroll = () => {
    if (!scrollEl || reachedEnd) return;
    if (scrollEl.scrollTop + scrollEl.clientHeight >= scrollEl.scrollHeight - 8) setReached();
  };

  const tick = () => {
    const now = performance.now();
    // Cap each step so a suspended device or throttled timer cannot bank idle time.
    if (!document.hidden) activeMs += Math.min(now - lastTick, 1000);
    lastTick = now;
    emitProgress();
    maybeVerify();
  };

  const onVisibility = () => {
    lastTick = performance.now();
  };

  const timer = setInterval(tick, TICK_MS);
  document.addEventListener('visibilitychange', onVisibility);
  scrollEl?.addEventListener('scroll', checkScroll, { passive: true });
  if ('IntersectionObserver' in window && sentinelEl) {
    observer = new IntersectionObserver((entries) => {
      if (entries.some((e) => e.isIntersecting)) setReached();
    }, { threshold: 0 });
    observer.observe(sentinelEl);
  }
  emitProgress();

  return {
    requiredMs,
    get activeMs() { return activeMs; },
    get reachedEnd() { return reachedEnd; },
    destroy() {
      destroyed = true;
      clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisibility);
      scrollEl?.removeEventListener('scroll', checkScroll);
      observer?.disconnect();
    }
  };
}

export function recordVerifiedRead(articleId, dwellMs) {
  const article = getArticle(articleId);
  if (!article) return { error: 'not_found', coins: 0 };
  return update((s) => {
    if (s.reads[articleId]) return { alreadyRewarded: true, ...s.reads[articleId] };
    const now = Date.now();
    rollActivity(s, now);
    const base = readCoinsFor(s);
    let coins = 0;
    let reason = null;
    if (s.activity.rewardedReadTs.length >= ECONOMY.read.maxRewardedPer10Min) {
      reason = 'velocity';
    } else {
      const room = Math.max(0, capFor(s) - s.activity.coinsEarned);
      coins = Math.min(base, room);
      if (coins === 0) reason = 'daily_cap';
    }

    s.reads[articleId] = { verifiedAt: now, dwellMs: Math.round(dwellMs), coins, reason };
    s.stats.readingMs += Math.round(dwellMs);
    s.stats.verifiedReads += 1;
    s.stats.categoryCounts[article.category] = (s.stats.categoryCounts[article.category] || 0) + 1;
    if (article.sponsor) s.stats.sponsoredReads += 1;
    if (!s.stats.openedIds.includes(articleId)) s.stats.openedIds.push(articleId);
    const streak = updateStreakOnRead(s, now);

    if (coins > 0) {
      addEntry(s, { type: 'read', amount: coins, title: `Verified read: ${article.title}`, meta: { articleId, ...(coins < base ? { cappedFrom: base } : {}) }, ts: now });
      s.activity.rewardedReadTs.push(now);
      s.activity.coinsEarned += coins;
    } else {
      addEntry(s, { type: 'rejected', amount: 0, title: 'Reading reward not applied', reason, meta: { articleId, source: 'read', articleTitle: article.title }, ts: now });
    }
    const referralPaid = maybePayReferral(s, now);
    return { coins, reason, base, capped: coins > 0 && coins < base, streak, referralPaid, alreadyRewarded: false };
  });
}
