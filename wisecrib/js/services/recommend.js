// Feed ranking: goal weights + interests + reading history + exam-tag match + recency.
// Recency is measured from CONTENT_NOW; demo dates are shifted uniformly, so the ranking is stable over time.
import { ARTICLES, CONTENT_NOW, getArticle } from '../data/articles.js';
import { getGoal } from '../data/goals.js';
import { getCategory } from '../data/categories.js';

const DAY = 86400000;

export function examTagMatch(article, goal) {
  return article.examTags.some((tag) => goal.tagMatches.some((m) => tag.startsWith(m)));
}

export function scoreArticle(article, state) {
  const goal = getGoal(state.profile.goal);
  const interests = state.profile.interests || [];
  let score = 0;
  score += (goal.weights[article.category] || 0) * 2; // 0..6
  if (interests.includes(article.category)) score += 3;
  const history = state.stats.categoryCounts[article.category] || 0;
  score += Math.min(2, history * 0.5);
  if (examTagMatch(article, goal)) score += 2;
  const days = Math.max(0, (CONTENT_NOW - new Date(article.publishedAt).getTime()) / DAY);
  score += Math.max(0, 3 - days * 0.12); // 0..3, fades over ~25 days
  if (state.reads[article.id]) score -= 8; // already verified: sink below unread
  return score;
}

export function rankFeed(state, list = ARTICLES) {
  return list
    .map((a) => ({ a, s: scoreArticle(a, state) }))
    .sort((x, y) => y.s - x.s || y.a.publishedAt.localeCompare(x.a.publishedAt))
    .map((x) => x.a);
}

export function filterByCategory(list, key) {
  return !key || key === 'all' ? list : list.filter((a) => a.category === key);
}

export function search(list, query) {
  const tokens = String(query || '').toLowerCase().split(/\s+/).filter(Boolean);
  if (!tokens.length) return list;
  return list.filter((a) => {
    const hay = [a.title, a.dek, a.source.publisherName, getCategory(a.category).label, ...a.examTags, ...a.keyFacts].join(' ').toLowerCase();
    return tokens.every((t) => hay.includes(t));
  });
}

export function relatedArticles(article, state, n = 3) {
  return rankFeed(state, ARTICLES.filter((a) => a.category === article.category && a.id !== article.id)).slice(0, n);
}

export function nextUnread(state, excludeId = null) {
  return rankFeed(state).find((a) => a.id !== excludeId && !state.reads[a.id]) || null;
}

export { getArticle };
