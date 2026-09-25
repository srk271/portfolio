// Onboarding: 3 steps (name -> goal -> interests + optional referral code). State kept in the view module.
import { html, mount, delegate, disposer } from '../dom.js';
import { on, emit } from '../bus.js';
import { ECONOMY } from '../config.js';
import { GOALS, getGoal } from '../data/goals.js';
import { CATEGORIES } from '../data/categories.js';
import { icon, coinIcon, logoMark } from '../ui/icons.js';
import { validateCode } from '../services/referral.js';
import { completeOnboarding } from '../services/profile.js';

const freshDraft = () => ({ step: 1, name: '', goal: null, interests: [], interestsTouched: false, referral: '' });
let draft = freshDraft();
on('store:reset', () => {
  draft = freshDraft();
});

const MIN_INTERESTS = 2;

// The form keeps its input when another tab changes the state (the router still redirects once onboarded).
export const keepOnExternalChange = true;

function dots(step) {
  return html`<div class="onb-dots" role="img" aria-label="${`Step ${step} of 3`}">
    ${[1, 2, 3].map((n) => html`<span class="${n === step ? 'is-active' : n < step ? 'is-done' : ''}"></span>`)}
  </div>`;
}

function stepOne() {
  return html`<div class="onb onb--hero">
    <div class="onb-top">${dots(1)}</div>
    <section class="onb-hero">
      <div class="onb-logo">${logoMark()}</div>
      <h1 class="onb-brand"><span class="wordmark">WiSe<b>CRiB</b></span></h1>
      <p class="onb-tagline">Read the news. Prove it with a quiz. Earn rewards.</p>
      <ol class="onb-loop" aria-label="How it works">
        <li>${icon('newspaper')}<span>Read</span></li>
        <li>${icon('target')}<span>Quiz</span></li>
        <li>${coinIcon()}<span>Earn</span></li>
        <li>${icon('gift')}<span>Redeem</span></li>
      </ol>
    </section>
    <form class="onb-form" data-form="1" novalidate>
      <p class="card-note is-info">${icon('info')}<span><strong>Demo prototype</strong> — content and rewards are simulated. No real money moves.</span></p>
      <label class="field">
        <span class="field-label">What should we call you?</span>
        <input class="input" name="name" maxlength="24" autocomplete="given-name" placeholder="Your first name" data-testid="onboarding-name" value="${draft.name}" aria-describedby="name-error">
        <span class="field-error" id="name-error" role="alert"></span>
      </label>
      <button class="btn btn-primary btn-block btn-lg" type="submit" data-testid="onboarding-next">Continue</button>
    </form>
  </div>`;
}

function stepTwo() {
  return html`<div class="onb">
    <div class="onb-top"><button type="button" class="icon-btn" data-action="back" aria-label="Back">${icon('back')}</button>${dots(2)}<span class="onb-spacer"></span></div>
    <form class="onb-form" data-form="2" novalidate>
      <fieldset class="goal-list">
        <legend class="onb-q"><span class="h-display">Hi ${draft.name}, what are you preparing for?</span><span class="muted">We'll shape your feed around it. You can change this later.</span></legend>
        ${GOALS.map((g) => html`<label class="goal-card ${draft.goal === g.key ? 'is-selected' : ''}" data-testid="goal-${g.key}">
          <input type="radio" name="goal" value="${g.key}" ${draft.goal === g.key ? html`checked` : ''}>
          <span class="goal-icon">${icon(g.icon)}</span>
          <span class="goal-text"><span class="goal-title">${g.label}</span><span class="goal-desc">${g.description}</span></span>
          <span class="goal-check" aria-hidden="true">${icon('check')}</span>
        </label>`)}
      </fieldset>
      <span class="field-error" id="goal-error" role="alert"></span>
      <button class="btn btn-primary btn-block btn-lg" type="submit" data-testid="onboarding-next" ${draft.goal ? '' : html`disabled`}>Continue</button>
    </form>
  </div>`;
}

function stepThree() {
  return html`<div class="onb">
    <div class="onb-top"><button type="button" class="icon-btn" data-action="back" aria-label="Back">${icon('back')}</button>${dots(3)}<span class="onb-spacer"></span></div>
    <form class="onb-form" data-form="3" novalidate>
      <div class="onb-q"><h1 class="h-display">Pick your interests</h1><span class="muted">Choose at least ${MIN_INTERESTS}. We pre-selected a few for ${getGoal(draft.goal).label}.</span></div>
      <div class="interest-grid" role="group" aria-label="Interests">
        ${CATEGORIES.map((c) => html`<button type="button" class="chip chip-lg" data-action="interest" data-key="${c.key}" data-testid="interest-${c.key}" aria-pressed="${draft.interests.includes(c.key) ? 'true' : 'false'}" style="--cat:${c.color}"><span class="dot"></span>${c.label}</button>`)}
      </div>
      <span class="field-error" id="interest-error" role="alert"></span>
      <label class="field">
        <span class="field-label">Referral code <span class="muted">(optional)</span></span>
        <input class="input" name="referral" autocomplete="off" autocapitalize="characters" spellcheck="false" placeholder="${`${ECONOMY.referral.codePrefix}7K2Q`}" data-testid="referral-input" value="${draft.referral}" aria-describedby="ref-hint ref-error">
        <span class="field-hint" id="ref-hint">A friend's code pays +${ECONOMY.referral.reward} coins after your first ${ECONOMY.referral.requiredVerifiedReads} verified reads.</span>
        <span class="field-error" id="ref-error" role="alert"></span>
      </label>
      <div class="onb-welcome">${coinIcon()}<span>You'll start with <strong>+${ECONOMY.welcomeBonus} welcome coins</strong>.</span></div>
      <button class="btn btn-gold btn-block btn-lg" type="submit" data-testid="onboarding-finish">Start reading</button>
      <a class="link-btn center" href="#/how-rewards-work?from=onboarding">How rewards work</a>
    </form>
  </div>`;
}

export function render(container, ctx) {
  const dispose = disposer();

  const draw = () => {
    mount(container, draft.step === 1 ? stepOne() : draft.step === 2 ? stepTwo() : stepThree());
    container.scrollTop = 0;
  };

  const setError = (id, message) => {
    const el = container.querySelector(`#${id}`);
    if (el) el.textContent = message || '';
  };

  dispose.add(delegate(container, 'input', 'input[name="name"]', (e, input) => {
    draft.name = input.value.slice(0, 24);
    input.removeAttribute('aria-invalid');
    setError('name-error', '');
  }));
  dispose.add(delegate(container, 'input', 'input[name="referral"]', (e, input) => {
    draft.referral = input.value;
    input.removeAttribute('aria-invalid');
    setError('ref-error', '');
  }));
  dispose.add(delegate(container, 'change', 'input[name="goal"]', (e, input) => {
    draft.goal = input.value;
    if (!draft.interestsTouched) draft.interests = [...getGoal(draft.goal).defaultInterests];
    container.querySelectorAll('.goal-card').forEach((card) => card.classList.toggle('is-selected', card.querySelector('input').checked));
    container.querySelector('[data-testid="onboarding-next"]').disabled = false;
    setError('goal-error', '');
  }));
  dispose.add(delegate(container, 'click', '[data-action]', (e, el) => {
    const action = el.dataset.action;
    if (action === 'back') {
      draft.step = Math.max(1, draft.step - 1);
      draw();
    } else if (action === 'interest') {
      const key = el.dataset.key;
      draft.interestsTouched = true;
      draft.interests = draft.interests.includes(key) ? draft.interests.filter((k) => k !== key) : [...draft.interests, key];
      el.setAttribute('aria-pressed', String(draft.interests.includes(key)));
      if (draft.interests.length >= MIN_INTERESTS) setError('interest-error', '');
    }
  }));

  dispose.add(delegate(container, 'submit', 'form', (e, form) => {
    e.preventDefault();
    const step = Number(form.dataset.form);
    if (step === 1) {
      const name = draft.name.trim();
      if (!name) {
        const input = form.querySelector('input[name="name"]');
        input.setAttribute('aria-invalid', 'true');
        setError('name-error', 'Please enter a name (1–24 characters).');
        input.focus();
        return;
      }
      draft.name = name;
      draft.step = 2;
      draw();
    } else if (step === 2) {
      if (!draft.goal) {
        setError('goal-error', 'Choose one to continue.');
        return;
      }
      draft.step = 3;
      if (!draft.interestsTouched && !draft.interests.length) draft.interests = [...getGoal(draft.goal).defaultInterests];
      draw();
    } else {
      if (draft.interests.length < MIN_INTERESTS) {
        setError('interest-error', `Pick at least ${MIN_INTERESTS} interests.`);
        return;
      }
      const ref = validateCode(draft.referral, null);
      if (!ref.ok) {
        const input = form.querySelector('input[name="referral"]');
        input.setAttribute('aria-invalid', 'true');
        setError('ref-error', ref.error);
        input.focus();
        return;
      }
      const result = completeOnboarding({ name: draft.name, goal: draft.goal, interests: draft.interests, referralCode: ref.code });
      draft = freshDraft();
      ctx.navigate('#/feed');
      setTimeout(() => {
        emit('coins:awarded', { amount: result.welcome, message: `+${result.welcome} welcome coins` });
      }, 350);
    }
  }));

  // Pre-select goal defaults if the user skipped ahead and came back.
  if (draft.step === 3 && !draft.goal) draft.step = 2;
  draw();
  return dispose;
}
