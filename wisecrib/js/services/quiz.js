// Demo only — real enforcement (device/account checks, server-side timers, fraud scoring) belongs on the server.
//
// Article quizzes (one rewarded attempt, retries are practice), the daily current-affairs quiz and the
// premium mock test. Correct answers stay in JS data and never go into DOM attributes.
import { update, getState } from '../store.js';
import { ECONOMY } from '../config.js';
import { ARTICLES, getArticle } from '../data/articles.js';
import { dateKey, hashString, seededShuffle, mulberry32 } from '../format.js';
import { addEntry, awardCapped } from './wallet.js';

export function grade(questions, answers) {
  const perQuestion = questions.map((q, i) => ({ chosen: answers[i], answer: q.answer, correct: answers[i] === q.answer }));
  const correct = perQuestion.filter((p) => p.correct).length;
  return { correct, total: questions.length, perQuestion };
}

export function quizCoinsFor(state = getState()) {
  return Math.ceil(ECONOMY.articleQuiz.passCoins * (state.premium.active ? ECONOMY.premium.multiplier : 1));
}

export function articleQuizState(articleId, state = getState()) {
  const record = state.quizzes[articleId] || null;
  return { verified: Boolean(state.reads[articleId]), record, rewardUsed: Boolean(record?.rewardedAttemptAt) };
}

export function submitArticleQuiz(articleId, answers) {
  const article = getArticle(articleId);
  if (!article) return { error: 'not_found' };
  const result = grade(article.quiz, answers);
  const passMark = ECONOMY.articleQuiz.passMark;
  return update((s) => {
    if (!s.reads[articleId]) return { ...result, locked: true, coins: 0, sponsorCoins: 0 };
    const rec = s.quizzes[articleId] || { attempts: 0, rewardedAttemptAt: null, correct: 0, total: result.total, passed: false, coins: 0, sponsorCoins: 0 };
    rec.attempts += 1;
    const passed = result.correct >= passMark;
    if (rec.rewardedAttemptAt) {
      s.quizzes[articleId] = rec;
      return { ...result, passed, practice: true, coins: 0, sponsorCoins: 0 };
    }
    const now = Date.now();
    rec.rewardedAttemptAt = now; // consumed on pass or fail
    rec.correct = result.correct;
    rec.total = result.total;
    rec.passed = passed;
    s.stats.quizAnswered += result.total;
    s.stats.quizCorrect += result.correct;
    let coins = 0;
    let sponsorCoins = 0;
    let reason = null;
    let capped = false;
    const want = quizCoinsFor(s);
    if (passed) {
      const r = awardCapped(s, { type: 'quiz', amount: want, title: `Quiz passed: ${article.title}`, meta: { articleId, score: `${result.correct}/${result.total}` }, now });
      coins = r.coins;
      reason = r.reason;
      capped = r.capped;
      if (article.sponsor?.quizBonus) {
        sponsorCoins = article.sponsor.quizBonus;
        addEntry(s, { type: 'sponsor_bonus', amount: sponsorCoins, title: `${article.sponsor.name} quiz bonus (brand-funded)`, meta: { articleId }, ts: now });
      }
    }
    rec.coins = coins;
    rec.sponsorCoins = sponsorCoins;
    s.quizzes[articleId] = rec;
    return { ...result, passed, practice: false, coins, sponsorCoins, reason, capped, want };
  });
}

// ---------------------------------------------------------------- Daily quiz / mock test

function recentIds(n) {
  return [...ARTICLES].sort((a, b) => b.publishedAt.localeCompare(a.publishedAt)).slice(0, n).map((a) => a.id);
}

// Deterministic for a given day and reading history: read articles first, then the most recent ones.
export function buildDailyQuiz(key, state = getState()) {
  const n = ECONOMY.dailyQuiz.questions;
  const seed = hashString(`daily:${key}`);
  const read = seededShuffle(Object.keys(state.reads).filter((id) => getArticle(id)), seed);
  const recent = seededShuffle(recentIds(14).filter((id) => !read.includes(id)), seed + 1);
  const rest = seededShuffle(ARTICLES.map((a) => a.id).filter((id) => !read.includes(id) && !recent.includes(id)), seed + 2);
  const chosen = [...read, ...recent, ...rest].slice(0, n);
  const rand = mulberry32(seed + 3);
  return seededShuffle(chosen, seed + 4).map((articleId) => ({ articleId, qIndex: Math.floor(rand() * getArticle(articleId).quiz.length) }));
}

export function buildMockTest(key) {
  const n = ECONOMY.mockTest.questions;
  const seed = hashString(`mock:${key}`);
  const chosen = seededShuffle(ARTICLES.map((a) => a.id), seed).slice(0, n);
  const rand = mulberry32(seed + 7);
  return chosen.map((articleId) => ({ articleId, qIndex: Math.floor(rand() * getArticle(articleId).quiz.length) }));
}

export function resolveRefs(refs) {
  return refs
    .map(({ articleId, qIndex }) => {
      const article = getArticle(articleId);
      const q = article?.quiz[qIndex];
      if (!q) return null;
      return { ...q, fact: article.keyFacts[q.factRef], articleId, articleTitle: article.title };
    })
    .filter(Boolean);
}

export function dailyQuizRecord(state = getState(), key = dateKey()) {
  return state.dailyQuiz[key] || null;
}

export function startDailyQuiz(key = dateKey()) {
  return update((s) => {
    if (!s.dailyQuiz[key]) {
      s.dailyQuiz[key] = { questionRefs: buildDailyQuiz(key, s), startedAt: Date.now(), completedAt: null, correct: 0, total: ECONOMY.dailyQuiz.questions, coins: 0 };
    }
    return { key, record: s.dailyQuiz[key] };
  });
}

// Grades the quiz started under startedKey and credits it to the day it is submitted (creditKey), so a
// quiz opened at 23:59 and submitted at 00:01 counts for the new day, and never twice for one day.
// If creditKey's quiz is already complete (or this very quiz was completed in another tab), the attempt
// is graded but earns nothing ({ already: true }).
export function submitDailyQuiz(startedKey, answers, creditKey = dateKey()) {
  return update((s) => {
    const started = s.dailyQuiz[startedKey];
    if (!started) return { error: 'not_started' };
    const questions = resolveRefs(started.questionRefs);
    const result = grade(questions, answers);
    const existing = s.dailyQuiz[creditKey] || null;
    const rolledOver = creditKey !== startedKey;
    if (existing?.completedAt || started.completedAt) return { ...result, already: true, coins: 0, want: 0, key: creditKey, rolledOver };
    const { coinsPerCorrect, perfectBonus } = ECONOMY.dailyQuiz;
    const perfect = result.correct === result.total;
    const want = result.correct * coinsPerCorrect + (perfect ? perfectBonus : 0);
    let coins = 0;
    let reason = null;
    if (want > 0) {
      const r = awardCapped(s, { type: 'daily_quiz', amount: want, title: `Daily quiz · ${result.correct}/${result.total}${perfect ? ' (perfect)' : ''}`, meta: { date: creditKey } });
      coins = r.coins;
      reason = r.reason;
    }
    s.dailyQuiz[creditKey] = {
      ...(existing || {}),
      questionRefs: started.questionRefs,
      startedAt: started.startedAt,
      completedAt: Date.now(),
      correct: result.correct,
      total: result.total,
      coins,
      answers: [...answers]
    };
    s.stats.quizAnswered += result.total;
    s.stats.quizCorrect += result.correct;
    return { ...result, coins, want, reason, perfect, already: false, key: creditKey, rolledOver };
  });
}

export function mockTestRecord(state = getState(), key = dateKey()) {
  return state.mockTest[key] || null;
}

export function startMockTest(key = dateKey()) {
  return update((s) => {
    if (!s.premium.active) return { locked: true };
    if (!s.mockTest[key]) {
      s.mockTest[key] = { questionRefs: buildMockTest(key), startedAt: Date.now(), completedAt: null, correct: 0, total: ECONOMY.mockTest.questions, coins: 0 };
    }
    return { key, record: s.mockTest[key] };
  });
}

// Same day-crediting rule as submitDailyQuiz.
export function submitMockTest(startedKey, answers, creditKey = dateKey()) {
  return update((s) => {
    const started = s.mockTest[startedKey];
    if (!started) return { error: 'not_started' };
    const questions = resolveRefs(started.questionRefs);
    const result = grade(questions, answers);
    const existing = s.mockTest[creditKey] || null;
    const rolledOver = creditKey !== startedKey;
    if (existing?.completedAt || started.completedAt) return { ...result, already: true, coins: 0, want: 0, key: creditKey, rolledOver };
    if (!s.premium.active) return { ...result, locked: true, coins: 0, want: 0, key: creditKey, rolledOver };
    const want = result.correct * ECONOMY.mockTest.coinsPerCorrect;
    let coins = 0;
    let reason = null;
    if (want > 0) {
      const r = awardCapped(s, { type: 'mock_test', amount: want, title: `Mock test · ${result.correct}/${result.total}`, meta: { date: creditKey } });
      coins = r.coins;
      reason = r.reason;
    }
    s.mockTest[creditKey] = {
      ...(existing || {}),
      questionRefs: started.questionRefs,
      startedAt: started.startedAt,
      completedAt: Date.now(),
      correct: result.correct,
      total: result.total,
      coins,
      answers: [...answers]
    };
    s.stats.quizAnswered += result.total;
    s.stats.quizCorrect += result.correct;
    return { ...result, coins, want, reason, already: false, key: creditKey, rolledOver };
  });
}
