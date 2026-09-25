// Practice hub (#/practice), daily current-affairs quiz (#/practice/daily) and premium mock test (#/practice/mock).
import { html, mount } from '../dom.js';
import { emit } from '../bus.js';
import { getState } from '../store.js';
import { ECONOMY } from '../config.js';
import { ARTICLE_MAP } from '../data/articles.js';
import { getCategory } from '../data/categories.js';
import { dateKey, fmtPercent, fmtNumber } from '../format.js';
import { icon, coinIcon, crownIcon } from '../ui/icons.js';
import { renderQuiz, renderQuizResult } from '../ui/quizEngine.js';
import {
  dailyQuizRecord, startDailyQuiz, submitDailyQuiz, resolveRefs,
  mockTestRecord, startMockTest, submitMockTest, quizCoinsFor
} from '../services/quiz.js';
import { reasonText } from '../services/wallet.js';

const maxDailyCoins = () => ECONOMY.dailyQuiz.questions * ECONOMY.dailyQuiz.coinsPerCorrect + ECONOMY.dailyQuiz.perfectBonus;

function dailyCard(state) {
  const rec = dailyQuizRecord(state);
  if (rec?.completedAt) {
    return html`<section class="practice-card card card-ink" data-testid="daily-quiz-card" data-state="done">
      <div class="row-between"><span class="h-sub on-ink">Daily current-affairs quiz</span><span class="tag tag-success">${icon('check')}Done</span></div>
      <h2 class="practice-title">Completed today · ${rec.correct}/${rec.total}</h2>
      <p class="on-ink-muted">You earned +${rec.coins} coins. Come back tomorrow for a new set of ${ECONOMY.dailyQuiz.questions}.</p>
      <a class="btn btn-on-ink" href="#/practice/daily" data-testid="daily-quiz-review">Review answers</a>
    </section>`;
  }
  return html`<section class="practice-card card card-ink" data-testid="daily-quiz-card" data-state="${rec ? 'in-progress' : 'available'}">
    <div class="row-between"><span class="h-sub on-ink">Daily current-affairs quiz</span><span class="tag tag-on-ink">${ECONOMY.dailyQuiz.questions} Qs</span></div>
    <h2 class="practice-title">${rec ? 'Pick up where you left off' : "Today's quiz is ready"}</h2>
    <p class="on-ink-muted">Questions from your reads and recent explainers. +${ECONOMY.dailyQuiz.coinsPerCorrect} coin per correct answer, +${ECONOMY.dailyQuiz.perfectBonus} for a perfect score (up to +${maxDailyCoins()}). Once a day.</p>
    <a class="btn btn-gold" href="#/practice/daily" data-testid="daily-quiz-start">${rec ? 'Continue quiz' : 'Start quiz'}</a>
  </section>`;
}

function mockCard(state) {
  const { questions, minutes, coinsPerCorrect } = ECONOMY.mockTest;
  if (!state.premium.active) {
    return html`<section class="practice-card card is-locked" data-testid="mock-card" data-state="locked">
      <div class="row-between"><span class="h-sub">Daily mock test</span><span class="badge-premium">${crownIcon()}Premium</span></div>
      <h2 class="practice-title">${questions} questions · ${minutes}-minute timer</h2>
      <p class="muted">Exam-style practice across every category. +${coinsPerCorrect} coin per correct answer, once a day.</p>
      <a class="btn btn-primary" href="#/premium" data-testid="mock-upgrade">${icon('lock')}Upgrade to unlock</a>
    </section>`;
  }
  const rec = mockTestRecord(state);
  const done = Boolean(rec?.completedAt);
  return html`<section class="practice-card card" data-testid="mock-card" data-state="${done ? 'done' : 'available'}">
    <div class="row-between"><span class="h-sub">Daily mock test</span><span class="badge-premium">${crownIcon()}Premium</span></div>
    <h2 class="practice-title">${done ? `Completed today · ${rec.correct}/${rec.total}` : `${questions} questions · ${minutes}-minute timer`}</h2>
    <p class="muted">${done ? `+${rec.coins} coins earned. A new test unlocks tomorrow.` : `+${coinsPerCorrect} coin per correct answer. The timer is a guide; you can still submit after it ends.`}</p>
    <a class="btn ${done ? 'btn-secondary' : 'btn-primary'}" href="#/practice/mock" data-testid="mock-start">${done ? 'Review answers' : rec ? 'Continue test' : 'Start mock test'}</a>
  </section>`;
}

export function render(container) {
  const state = getState();
  const st = state.stats;
  const accuracy = st.quizAnswered ? fmtPercent(st.quizCorrect / st.quizAnswered) : '—';
  const verifiedIds = Object.keys(state.reads).filter((id) => ARTICLE_MAP[id]).sort((a, b) => state.reads[b].verifiedAt - state.reads[a].verifiedAt);
  const ready = verifiedIds.filter((id) => !state.quizzes[id]?.rewardedAttemptAt);
  const done = verifiedIds.filter((id) => state.quizzes[id]?.rewardedAttemptAt);
  const coins = quizCoinsFor(state);

  mount(container, html`<div class="page practice">
    <header class="page-head">
      <h1 class="h-display" data-large-title>Practice</h1>
      <p class="muted">Quizzes turn reading into recall. Quiz coins count toward today's activity cap.</p>
    </header>
    ${dailyCard(state)}
    ${mockCard(state)}
    <div class="tiles tiles-3">
      <div class="tile"><div class="tile-value" data-testid="practice-accuracy">${accuracy}</div><div class="tile-label">Quiz accuracy</div></div>
      <div class="tile"><div class="tile-value">${fmtNumber(st.quizAnswered)}</div><div class="tile-label">Questions answered</div></div>
      <div class="tile"><div class="tile-value">${fmtNumber(done.length)}</div><div class="tile-label">Article quizzes done</div></div>
    </div>
    <section class="stack-sm">
      <div class="section-head"><h2 class="h-section">Article quizzes</h2><span class="muted small">${ready.length} ready</span></div>
      ${verifiedIds.length ? html`<div class="card list" data-testid="article-quiz-list">
        ${[...ready, ...done].map((id) => {
          const a = ARTICLE_MAP[id];
          const q = state.quizzes[id];
          const cat = getCategory(a.category);
          return html`<a class="list-row" href="#/quiz/${id}" data-testid="article-quiz-row" data-state="${q?.rewardedAttemptAt ? 'done' : 'ready'}">
            <span class="list-dot" style="--cat:${cat.color}"></span>
            <span class="list-row-main"><span class="list-row-title">${a.title}</span><span class="list-row-sub">${cat.short} · ${q?.rewardedAttemptAt ? `Score ${q.correct}/${q.total}${q.coins + q.sponsorCoins ? ` · +${q.coins + q.sponsorCoins} coins` : ''}` : `Ready · +${coins} coins${a.sponsor ? ` +${a.sponsor.quizBonus} bonus` : ''}`}</span></span>
            ${q?.rewardedAttemptAt ? html`<span class="tag tag-success">${icon('check')}Done</span>` : html`<span class="reward-badge">${coinIcon()}+${coins}</span>`}
          </a>`;
        })}
      </div>` : html`<div class="empty card" data-testid="article-quiz-empty">${icon('book')}<div class="empty-title">Read an article to unlock its quiz</div><p>Every verified read unlocks a 3-question quiz.</p><a class="btn btn-primary" href="#/feed">Browse the feed</a></div>`}
    </section>
  </div>`);
  return null;
}

// ---------------------------------------------------------------- Daily quiz

function dailyReward(r) {
  if (r.coins > 0) return { kind: 'coins', html: html`<b>+${r.coins} coins</b> added${r.perfect ? ' (perfect score bonus included)' : ''}.${r.coins < r.want ? ' Capped by today\'s activity limit.' : ''}` };
  if (r.want > 0) return { kind: 'muted', html: html`<b>Reward not applied</b> — ${reasonText(r.reason)}.` };
  return { kind: 'muted', html: html`<b>No coins this time.</b> Read a few explainers and try again tomorrow.` };
}

function renderDailyResult(container, key, questions, answers, r, fresh) {
  renderQuizResult(container, {
    heading: fresh ? 'Daily quiz result' : 'Completed today',
    correct: r.correct,
    total: r.total,
    passed: r.correct >= Math.ceil(r.total / 2),
    message: r.correct === r.total ? 'Perfect score!' : fresh ? 'Quiz complete' : 'Come back tomorrow',
    reward: fresh ? dailyReward(r) : { kind: r.coins ? 'coins' : 'muted', html: html`<b>+${r.coins} coins</b> earned today. A new set unlocks tomorrow.` },
    questions,
    answers,
    actions: html`<a class="btn btn-primary btn-block" href="#/practice" data-testid="daily-back">Back to practice</a><a class="btn btn-secondary btn-block" href="#/feed">Read more explainers</a>`
  });
}

export const daily = {
  keepOnExternalChange: true, // answers in progress; submit re-checks the latest state
  render(container) {
    const key = dateKey();
    const existing = dailyQuizRecord(getState(), key);
    if (existing?.completedAt) {
      const questions = resolveRefs(existing.questionRefs);
      if (Array.isArray(existing.answers) && existing.answers.length === questions.length) {
        renderDailyResult(container, key, questions, existing.answers, existing, false);
      } else {
        mount(container, html`<div class="page"><div class="empty card">${icon('checkCircle')}<div class="empty-title">Completed today · ${existing.correct}/${existing.total}</div><p>Come back tomorrow for a new set.</p><a class="btn btn-primary" href="#/practice">Back to practice</a></div></div>`);
      }
      return null;
    }
    const { record } = startDailyQuiz(key);
    const questions = resolveRefs(record.questionRefs);
    return renderQuiz(container, {
      kicker: new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'short' }),
      heading: 'Daily current-affairs quiz',
      subheading: `${questions.length} questions · +${ECONOMY.dailyQuiz.coinsPerCorrect} per correct · +${ECONOMY.dailyQuiz.perfectBonus} perfect bonus · once a day`,
      questions,
      onSubmit: (answers) => {
        const r = submitDailyQuiz(key, answers);
        renderDailyResult(container, key, questions, answers, r, true);
        if (r.coins > 0) {
          emit('coins:awarded', { amount: r.coins, from: container.querySelector('[data-testid="quiz-result-coins"]')?.getBoundingClientRect(), message: `Daily quiz · +${r.coins} coins` });
        } else if (r.want > 0 && r.reason) {
          emit('reward:rejected', { reason: r.reason });
        }
      }
    });
  }
};

// ---------------------------------------------------------------- Mock test (premium)

export const mock = {
  keepOnExternalChange: true,
  render(container) {
    const state = getState();
    if (!state.premium.active) {
      mount(container, html`<div class="page"><div class="empty card" data-testid="mock-locked">
        <span class="badge-premium">${crownIcon()}Premium</span>
        <div class="empty-title">The daily mock test is a Premium feature</div>
        <p>${ECONOMY.mockTest.questions} exam-style questions with a ${ECONOMY.mockTest.minutes}-minute timer, every day.</p>
        <a class="btn btn-primary" href="#/premium">See Premium</a>
      </div></div>`);
      return null;
    }
    const key = dateKey();
    const existing = mockTestRecord(state, key);
    const actions = html`<a class="btn btn-primary btn-block" href="#/practice" data-testid="mock-back">Back to practice</a>`;
    if (existing?.completedAt) {
      const questions = resolveRefs(existing.questionRefs);
      renderQuizResult(container, {
        heading: 'Mock test · completed today', correct: existing.correct, total: existing.total, passed: existing.correct >= existing.total / 2,
        message: 'Come back tomorrow', reward: { kind: existing.coins ? 'coins' : 'muted', html: html`<b>+${existing.coins} coins</b> earned today.` },
        questions, answers: existing.answers || [], actions
      });
      return null;
    }
    const { record } = startMockTest(key);
    const questions = resolveRefs(record.questionRefs);
    return renderQuiz(container, {
      kicker: 'Premium · Daily mock test',
      heading: `${questions.length}-question mock test`,
      subheading: `+${ECONOMY.mockTest.coinsPerCorrect} coin per correct answer · once a day`,
      questions,
      timer: { startedAt: record.startedAt, minutes: ECONOMY.mockTest.minutes },
      submitLabel: 'Submit test',
      onSubmit: (answers) => {
        const r = submitMockTest(key, answers);
        renderQuizResult(container, {
          heading: 'Mock test result', correct: r.correct, total: r.total, passed: r.correct >= r.total / 2,
          message: r.correct >= r.total * 0.8 ? 'Exam-ready!' : 'Test complete',
          reward: r.coins > 0 ? { kind: 'coins', html: html`<b>+${r.coins} coins</b> added.` } : { kind: 'muted', html: html`<b>No coins applied</b>${r.reason ? html` — ${reasonText(r.reason)}` : ''}.` },
          questions, answers, actions
        });
        if (r.coins > 0) emit('coins:awarded', { amount: r.coins, from: container.querySelector('[data-testid="quiz-result-coins"]')?.getBoundingClientRect(), message: `Mock test · +${r.coins} coins` });
      }
    });
  }
};
