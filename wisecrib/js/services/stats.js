// Derived stats for Profile and the Partners "this device" panel.
import { CATEGORIES } from '../data/categories.js';
import { SOURCE_LABELS } from './wallet.js';
import { streakDisplay } from './bonus.js';

export function coinsBySource(state) {
  const totals = {};
  for (const e of state.ledger) {
    if (e.amount > 0 && e.type !== 'demo_credit') totals[e.type] = (totals[e.type] || 0) + e.amount;
  }
  return Object.entries(totals)
    .map(([type, amount]) => ({ type, label: SOURCE_LABELS[type] || type, amount }))
    .sort((a, b) => b.amount - a.amount);
}

export function deriveStats(state) {
  const bySource = coinsBySource(state);
  const coinsEarned = bySource.reduce((sum, x) => sum + x.amount, 0);
  const st = state.stats;
  const accuracy = st.quizAnswered ? st.quizCorrect / st.quizAnswered : null;
  const categories = CATEGORIES.map((c) => ({ ...c, count: st.categoryCounts[c.key] || 0 }));
  const rejected = state.ledger.filter((e) => e.type === 'rejected').length;
  return {
    articlesRead: st.openedIds.length,
    verifiedReads: st.verifiedReads,
    coinsEarned,
    readingMs: st.readingMs,
    accuracy,
    quizAnswered: st.quizAnswered,
    quizCorrect: st.quizCorrect,
    streak: streakDisplay(state),
    categories,
    maxCategory: Math.max(1, ...categories.map((c) => c.count)),
    bySource,
    adImpressions: st.adImpressions,
    sponsoredReads: st.sponsoredReads,
    rejected,
    redemptions: state.redemptions.length
  };
}
