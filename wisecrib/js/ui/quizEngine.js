// Shared quiz UI: questions as fieldsets with radio options styled as cards, progress, optional
// countdown (display only), submit, and a result screen with per-question review.
// Correct answers are never written into the DOM before submission.
import { html, mount, delegate, disposer } from '../dom.js';
import { fmtClock } from '../format.js';
import { icon, coinIcon } from './icons.js';
import { announce } from './toast.js';

const KEYS = 'ABCD';

export function renderQuiz(container, {
  kicker = '',
  heading,
  subheading = '',
  notice = null,
  questions,
  timer = null, // { startedAt, minutes }
  submitLabel = 'Submit answers',
  onSubmit
}) {
  const dispose = disposer();
  const answers = new Array(questions.length).fill(null);
  const n = questions.length;

  mount(container, html`<div class="page quiz-page">
    <header class="quiz-head">
      ${kicker ? html`<div class="h-sub">${kicker}</div>` : ''}
      <h1 class="h-display">${heading}</h1>
      ${subheading ? html`<p class="muted" data-testid="quiz-subheading">${subheading}</p>` : ''}
      ${notice || ''}
      ${timer ? html`<div class="quiz-timer" data-testid="quiz-timer" role="timer" aria-live="off">${icon('clock')}<span>--:--</span></div>` : ''}
      <div class="quiz-progress"><div class="meter is-brand"><span data-progress></span></div><span class="quiz-count" data-count>0/${n} answered</span></div>
    </header>
    <form class="quiz-form" novalidate>
      ${questions.map((q, qi) => html`<fieldset class="quiz-q card">
        <legend>
          <span class="quiz-q-num">Question ${qi + 1} of ${n}</span>
          <span class="quiz-q-text">${q.q}</span>
          ${q.articleTitle ? html`<span class="quiz-q-from">From: ${q.articleTitle}</span>` : ''}
        </legend>
        <div class="options">
          ${q.options.map((opt, oi) => html`<label class="option" data-testid="quiz-option" data-q="${qi}" data-i="${oi}">
            <input type="radio" name="q${qi}" value="${oi}">
            <span class="option-key" aria-hidden="true">${KEYS[oi]}</span>
            <span class="option-text">${opt}</span>
          </label>`)}
        </div>
      </fieldset>`)}
      <div class="quiz-submit-bar">
        <button type="submit" class="btn btn-primary btn-block btn-lg" data-testid="quiz-submit" disabled>${submitLabel}</button>
      </div>
    </form>
  </div>`);

  const form = container.querySelector('.quiz-form');
  const submitBtn = container.querySelector('[data-testid="quiz-submit"]');
  const progressEl = container.querySelector('[data-progress]');
  const countEl = container.querySelector('[data-count]');

  const refresh = () => {
    const done = answers.filter((a) => a !== null).length;
    progressEl.style.width = `${(done / n) * 100}%`;
    countEl.textContent = `${done}/${n} answered`;
    submitBtn.disabled = done < n;
  };

  dispose.add(delegate(form, 'change', 'input[type="radio"]', (e, input) => {
    const qi = Number(input.name.slice(1));
    answers[qi] = Number(input.value);
    form.querySelectorAll(`input[name="q${qi}"]`).forEach((r) => r.closest('.option').classList.toggle('is-selected', r.checked));
    refresh();
  }));

  let submitted = false;
  const onSubmitEvt = (e) => {
    e.preventDefault();
    if (submitted || answers.some((a) => a === null)) return;
    submitted = true;
    dispose();
    onSubmit([...answers]);
  };
  form.addEventListener('submit', onSubmitEvt);
  dispose.add(() => form.removeEventListener('submit', onSubmitEvt));

  if (timer) {
    const el = container.querySelector('[data-testid="quiz-timer"]');
    const span = el.querySelector('span');
    const endAt = timer.startedAt + timer.minutes * 60000;
    const tick = () => {
      const left = endAt - Date.now();
      if (left <= 0) {
        span.textContent = "Time's up — you can still submit";
        el.classList.add('is-over');
        return false;
      }
      span.textContent = `${fmtClock(left)} left`;
      return true;
    };
    if (tick()) {
      const id = setInterval(() => {
        if (!tick()) clearInterval(id);
      }, 1000);
      dispose.add(() => clearInterval(id));
    }
  }

  refresh();
  return dispose;
}

// Result screen. reward: { kind: 'coins'|'muted', html } ; actions: html`` buttons/links.
// fresh: shown right after a submit, so focus moves to the result (the form it replaces had focus)
// and the score is announced, whether or not coins were awarded.
export function renderQuizResult(container, { heading, correct, total, passed, reward, actions, questions, answers, message, fresh = false }) {
  const pct = total ? correct / total : 0;
  const R = 52;
  const C = 2 * Math.PI * R;
  mount(container, html`<div class="page quiz-page">
    <section class="card card-ink result-hero" data-testid="quiz-result" tabindex="-1" aria-labelledby="quiz-result-title">
      <div class="h-sub" style="color: rgba(255,255,255,.75)">${heading}</div>
      <div class="score-ring" role="img" aria-label="${`Score ${correct} out of ${total}`}">
        <svg viewBox="0 0 120 120"><circle class="ring-track" cx="60" cy="60" r="${R}" fill="none" stroke-width="10"/><circle class="ring-fill" cx="60" cy="60" r="${R}" fill="none" stroke-width="10" stroke-linecap="round" stroke-dasharray="${C.toFixed(1)}" stroke-dashoffset="${(C * (1 - pct)).toFixed(1)}"/></svg>
        <div class="score-ring-label" data-testid="quiz-score"><span>${correct}<small>/${total}</small></span></div>
      </div>
      <div class="result-title" id="quiz-result-title">${message || (passed ? 'Nicely done!' : 'Keep going')}</div>
    </section>
    <div class="result-reward ${reward.kind === 'muted' ? 'is-muted' : ''}" data-testid="quiz-result-coins">
      ${reward.kind === 'muted' ? icon('info') : coinIcon()}
      <div>${reward.html}</div>
    </div>
    <div class="stack-sm">${actions}</div>
    <h2 class="h-section mt-2">Review</h2>
    <ol class="review">
      ${questions.map((q, i) => {
        const right = answers[i] === q.answer;
        return html`<li class="card review-item">
          <div class="quiz-q-num">Question ${i + 1}</div>
          <div class="review-q">${q.q}</div>
          <div class="review-ans ${right ? 'is-right' : 'is-wrong'}">${icon(right ? 'checkCircle' : 'x')}<span>${right ? 'Correct' : 'Your answer'}: ${q.options[answers[i]]}</span></div>
          ${right ? '' : html`<div class="review-ans is-right">${icon('checkCircle')}<span>Correct answer: ${q.options[q.answer]}</span></div>`}
          <div class="review-fact"><strong>Key fact:</strong> ${q.fact}${q.articleTitle ? html` <span class="muted">(${q.articleTitle})</span>` : ''}</div>
        </li>`;
      })}
    </ol>
  </div>`);
  container.scrollTop = 0;
  if (fresh) {
    container.querySelector('[data-testid="quiz-result"]').focus({ preventScroll: true });
    const rewardText = container.querySelector('[data-testid="quiz-result-coins"]').textContent.replace(/\s+/g, ' ').trim();
    announce(`Score ${correct} of ${total}. ${rewardText}`);
  }
}
