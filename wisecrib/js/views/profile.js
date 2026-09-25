// Profile: avatar, stats, category bars, goal + interests (inline edit), links.
import { html, mount, delegate, disposer } from '../dom.js';
import { getState } from '../store.js';
import { GOALS, getGoal } from '../data/goals.js';
import { CATEGORIES } from '../data/categories.js';
import { fmtCoins, fmtDuration, fmtPercent, fmtDate, hashString } from '../format.js';
import { icon, crownIcon } from '../ui/icons.js';
import { toast } from '../ui/toast.js';
import { deriveStats } from '../services/stats.js';
import { updateProfile } from '../services/profile.js';

const AVATAR_GRADIENTS = [
  ['#3F3DD8', '#7C4DFF'], ['#0B7A5E', '#2BC48A'], ['#1257B0', '#42A5F5'], ['#6A1B9A', '#D05CE3'],
  ['#C62828', '#FF7A59'], ['#D84E00', '#FFB300'], ['#6D4C41', '#C08552'], ['#26248A', '#F5B301']
];

let editing = false;
let editDraft = null;

function initials(name) {
  return String(name || '?').trim().split(/\s+/).slice(0, 2).map((p) => p[0]?.toUpperCase() || '').join('') || '?';
}

function template(state) {
  const st = deriveStats(state);
  const goal = getGoal(state.profile.goal);
  const [g1, g2] = AVATAR_GRADIENTS[hashString(state.profile.id || state.profile.name) % AVATAR_GRADIENTS.length];
  const interests = state.profile.interests.map((k) => CATEGORIES.find((c) => c.key === k)).filter(Boolean);
  return html`<div class="page profile">
    <section class="profile-head card card-pad">
      <div class="avatar" style="background: linear-gradient(135deg, ${g1}, ${g2})" aria-hidden="true">${initials(state.profile.name)}</div>
      <div class="profile-id">
        <h1 class="profile-name" data-testid="profile-name">${state.profile.name}</h1>
        <div class="muted small">${goal.label}${state.profile.createdAt ? ` · member since ${fmtDate(state.profile.createdAt, { month: 'short', year: 'numeric' })}` : ''}</div>
        ${state.premium.active ? html`<span class="badge-premium mt-2">${crownIcon()}Premium</span>` : html`<a class="tag tag-exam mt-2" href="#/premium">Free plan · See Premium</a>`}
      </div>
    </section>
    <div class="tiles" data-testid="profile-stats">
      <div class="tile"><div class="tile-value">${st.articlesRead}</div><div class="tile-label">Articles read</div></div>
      <div class="tile"><div class="tile-value" data-testid="stat-verified">${st.verifiedReads}</div><div class="tile-label">Verified reads</div></div>
      <div class="tile"><div class="tile-value">${fmtCoins(st.coinsEarned)}</div><div class="tile-label">Coins earned</div></div>
      <div class="tile"><div class="tile-value">${fmtDuration(st.readingMs)}</div><div class="tile-label">Verified reading time</div></div>
      <div class="tile"><div class="tile-value">${st.accuracy === null ? '—' : fmtPercent(st.accuracy)}</div><div class="tile-label">Quiz accuracy</div></div>
      <div class="tile"><div class="tile-value">${st.streak.current}<span class="tile-unit"> / ${st.streak.best}</span></div><div class="tile-label">Streak · current / best</div></div>
    </div>
    <section class="card card-pad stack-sm">
      <h2 class="h-section">Reads by category</h2>
      ${st.verifiedReads
        ? html`<div class="bars">${st.categories.map((c) => html`<div class="bar-row" style="--cat:${c.color}"><span class="bar-label">${c.short}</span><span class="bar-track"><span class="bar-fill" style="width:${((c.count / st.maxCategory) * 100).toFixed(1)}%"></span></span><span class="bar-val">${c.count}</span></div>`)}</div>`
        : html`<p class="muted small">Your verified reads will show up here by category.</p>`}
    </section>
    <section class="card card-pad stack-sm" data-testid="interests-card">
      <div class="row-between"><h2 class="h-section">Goal and interests</h2>${editing ? '' : html`<button type="button" class="btn btn-ghost btn-sm" data-action="edit" data-testid="edit-interests">Edit</button>`}</div>
      ${editing
        ? html`<div class="stack-sm">
            <label class="field"><span class="field-label">Goal</span>
              <select class="input" data-field="goal">${GOALS.map((g) => html`<option value="${g.key}" ${editDraft.goal === g.key ? html`selected` : ''}>${g.label}</option>`)}</select></label>
            <span class="field-label">Interests (at least 2)</span>
            <div class="interest-grid">${CATEGORIES.map((c) => html`<button type="button" class="chip" data-action="toggle" data-key="${c.key}" aria-pressed="${editDraft.interests.includes(c.key) ? 'true' : 'false'}" style="--cat:${c.color}"><span class="dot"></span>${c.short}</button>`)}</div>
            <span class="field-error" data-err></span>
            <div class="row"><button type="button" class="btn btn-primary btn-sm" data-action="save" data-testid="save-interests">Save</button><button type="button" class="btn btn-ghost btn-sm" data-action="cancel">Cancel</button></div>
          </div>`
        : html`<div class="muted small">Preparing for <b>${goal.label}</b></div>
          <div class="interest-grid">${interests.map((c) => html`<span class="chip" style="--cat:${c.color}"><span class="dot"></span>${c.short}</span>`)}</div>`}
    </section>
    <nav class="card list" aria-label="More">
      <a class="list-row" href="#/settings" data-testid="link-settings">${icon('sliders')}<span class="list-row-main"><span class="list-row-title">Settings</span><span class="list-row-sub">Theme, data export, demo tools</span></span>${icon('chevron', { cls: 'chev' })}</a>
      <a class="list-row" href="#/how-rewards-work">${icon('shield')}<span class="list-row-main"><span class="list-row-title">How rewards work</span><span class="list-row-sub">Earning, caps, fees and anti-fraud rules</span></span>${icon('chevron', { cls: 'chev' })}</a>
      <a class="list-row" href="#/partners" data-testid="link-partners">${icon('chart')}<span class="list-row-main"><span class="list-row-title">For partners & investors</span><span class="list-row-sub">Business model and unit economics</span></span>${icon('chevron', { cls: 'chev' })}</a>
    </nav>
  </div>`;
}

export function render(container) {
  const dispose = disposer();
  editing = false;
  const draw = () => {
    const top = container.scrollTop;
    mount(container, template(getState()));
    container.scrollTop = top;
  };
  draw();
  dispose.add(delegate(container, 'change', 'select[data-field="goal"]', (e, el) => {
    editDraft.goal = el.value;
  }));
  dispose.add(delegate(container, 'click', '[data-action]', (e, el) => {
    const action = el.dataset.action;
    if (action === 'edit') {
      const s = getState();
      editDraft = { goal: s.profile.goal, interests: [...s.profile.interests] };
      editing = true;
      draw();
    } else if (action === 'toggle') {
      const key = el.dataset.key;
      editDraft.interests = editDraft.interests.includes(key) ? editDraft.interests.filter((k) => k !== key) : [...editDraft.interests, key];
      el.setAttribute('aria-pressed', String(editDraft.interests.includes(key)));
    } else if (action === 'cancel') {
      editing = false;
      draw();
    } else if (action === 'save') {
      if (editDraft.interests.length < 2) {
        container.querySelector('[data-err]').textContent = 'Pick at least 2 interests.';
        return;
      }
      updateProfile({ goal: editDraft.goal, interests: editDraft.interests });
      editing = false;
      draw();
      toast({ message: 'Feed preferences saved', kind: 'success' });
    }
  }));
  return dispose;
}
