// Article quiz: guarded by a verified read; one rewarded attempt (pass or fail), retries are practice.
import { html, mount } from '../dom.js';
import { emit } from '../bus.js';
import { getState } from '../store.js';
import { ECONOMY } from '../config.js';
import { getArticle } from '../data/articles.js';
import { getCategory } from '../data/categories.js';
import { icon } from '../ui/icons.js';
import { renderQuiz, renderQuizResult } from '../ui/quizEngine.js';
import { articleQuizState, quizCoinsFor, submitArticleQuiz } from '../services/quiz.js';
import { nextUnread } from '../services/recommend.js';
import { reasonText } from '../services/wallet.js';

function rewardFor(r, article) {
  const { passMark, questions } = ECONOMY.articleQuiz;
  if (r.practice) {
    return { kind: 'muted', html: html`<b>Practice attempt — no coins.</b> The rewarded attempt for this article was already used.` };
  }
  if (!r.passed) {
    return { kind: 'muted', html: html`<b>Not passed</b> (needed ${passMark}/${questions}). The rewarded attempt is used; retries are practice.` };
  }
  const sponsorLine = r.sponsorCoins ? html`<br><span class="small">+${r.sponsorCoins} brand-funded bonus from ${article.sponsor.name} (outside the daily cap)</span>` : '';
  if (r.coins > 0) {
    return { kind: 'coins', html: html`<b>+${r.coins} coins</b> added to your wallet${r.capped ? ' (daily cap reached)' : ''}.${sponsorLine}` };
  }
  return { kind: r.sponsorCoins ? 'coins' : 'muted', html: html`<b>Quiz reward not applied</b> — ${reasonText(r.reason)}.${sponsorLine}` };
}

// Answers in progress are kept when another tab changes the state; submitting re-checks the latest state.
export const keepOnExternalChange = true;

export function render(container, ctx) {
  const article = getArticle(ctx.params.id);
  if (!article) {
    mount(container, html`<div class="page"><div class="empty card" data-testid="quiz-not-found">${icon('search')}<div class="empty-title">Quiz not found</div><a class="btn btn-primary" href="#/feed">Back to feed</a></div></div>`);
    return null;
  }
  const cat = getCategory(article.category);
  const st = articleQuizState(article.id);
  if (!st.verified) {
    mount(container, html`<div class="page"><div class="empty card" data-testid="quiz-locked">
      ${icon('lock')}
      <div class="empty-title">Read the article first</div>
      <p>The quiz unlocks after a verified read: reach the end and spend a little time with it.</p>
      <a class="btn btn-primary" href="#/article/${article.id}">Open the article</a>
    </div></div>`);
    return null;
  }

  const coins = quizCoinsFor(getState());
  const bonus = article.sponsor?.quizBonus;
  const { passMark, questions: n } = ECONOMY.articleQuiz;
  const subheading = st.rewardUsed
    ? 'Practice — already rewarded'
    : `One rewarded attempt · pass ${passMark}/${n} for +${coins} coins${bonus ? ` (+${bonus} brand-funded bonus)` : ''}`;
  const questions = article.quiz.map((q) => ({ ...q, fact: article.keyFacts[q.factRef] }));

  return renderQuiz(container, {
    kicker: `${cat.short} · Article quiz`,
    heading: article.title,
    subheading,
    notice: st.rewardUsed ? null : html`<p class="card-note is-info">${icon('info')}<span>Answers come from the Key facts box. Your first submission counts, pass or fail.</span></p>`,
    questions,
    onSubmit: (answers) => {
      const r = submitArticleQuiz(article.id, answers);
      const next = nextUnread(getState(), article.id);
      renderQuizResult(container, {
        heading: r.practice ? 'Practice result' : 'Quiz result',
        correct: r.correct,
        total: r.total,
        passed: r.passed,
        message: r.practice ? 'Practice complete' : r.passed ? 'Passed!' : 'Not this time',
        reward: rewardFor(r, article),
        questions,
        answers,
        fresh: true,
        actions: html`<a class="btn btn-primary btn-block" href="#/article/${article.id}" data-testid="quiz-back">Back to article</a>
          ${next ? html`<a class="btn btn-secondary btn-block" href="#/article/${next.id}" data-testid="quiz-next">Next article</a>` : ''}
          <button type="button" class="btn btn-ghost btn-block" data-testid="quiz-retry">Practice again</button>`
      });
      container.querySelector('[data-testid="quiz-retry"]').addEventListener('click', () => ctx.refresh());
      const total = (r.coins || 0) + (r.sponsorCoins || 0);
      const from = container.querySelector('[data-testid="quiz-result-coins"]')?.getBoundingClientRect();
      if (total > 0) {
        emit('coins:awarded', {
          amount: total,
          from,
          message: r.sponsorCoins ? `Quiz passed · +${r.coins} coins +${r.sponsorCoins} brand bonus` : `Quiz passed · +${r.coins} coins`,
          quiet: true
        });
      }
      if (!r.practice && r.passed && r.coins === 0 && r.reason) emit('reward:rejected', { reason: r.reason, quiet: true });
    }
  });
}
