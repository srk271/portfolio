// Personalized feed: search, "Today's brief", category chips, ranked story cards, ad placeholders (free users).
import { html, mount, delegate, disposer } from '../dom.js';
import { on } from '../bus.js';
import { getState } from '../store.js';
import { APP, ECONOMY } from '../config.js';
import { CATEGORIES, getCategory } from '../data/categories.js';
import { timeAgo, fmtNumber } from '../format.js';
import { icon, coinIcon } from '../ui/icons.js';
import { coverArt } from '../ui/artwork.js';
import { renderAdSlot } from '../ui/adslot.js';
import { rankFeed, filterByCategory, search } from '../services/recommend.js';
import { capProgress } from '../services/wallet.js';
import { streakDisplay } from '../services/bonus.js';
import { dailyQuizRecord } from '../services/quiz.js';
import { readCoinsFor } from '../services/reading.js';
import { quizCoinsFor } from '../services/quiz.js';

const freshFeedState = () => ({ category: 'all', query: '', scroll: 0 });
let feedState = freshFeedState();
on('store:reset', () => {
  feedState = freshFeedState();
});

export function rewardBadge(article, state) {
  const read = state.reads[article.id];
  const quiz = state.quizzes[article.id];
  if (read && quiz?.rewardedAttemptAt) return html`<span class="reward-badge is-done">${icon('check')}Quiz done</span>`;
  if (read) return html`<span class="reward-badge is-done">${icon('check')}Verified</span>`;
  return html`<span class="reward-badge" aria-label="${`Earn ${readCoinsFor(state)} coins for reading, ${quizCoinsFor(state)} for the quiz`}">${coinIcon()}+${readCoinsFor(state)} · quiz +${quizCoinsFor(state)}</span>`;
}

export function storyCard(article, variant, state) {
  const cat = getCategory(article.category);
  const lead = variant === 'lead';
  return html`<a class="story story--${variant} card" href="#/article/${article.id}" data-testid="feed-card" data-article-id="${article.id}">
    ${lead ? html`<div class="story-art">${coverArt(article, { variant: 'hero' })}</div>` : ''}
    <div class="story-body">
      <div class="story-tags">
        <span class="tag tag-cat" style="--cat:${cat.color}">${cat.short}</span>
        <span class="tag tag-demo">Demo</span>
        ${article.sponsor ? html`<span class="tag tag-sponsored">Sponsored</span>` : ''}
      </div>
      <h3 class="story-title">${article.title}</h3>
      ${lead ? html`<p class="story-dek">${article.dek}</p>` : ''}
      <div class="story-meta">${article.source.publisherName} · ${timeAgo(article.publishedAt)} · ${article.readingTime} min</div>
      <div class="story-foot">
        <span class="story-exams">${article.examTags.slice(0, 2).map((t) => html`<span class="tag tag-exam">${t}</span>`)}</span>
        ${rewardBadge(article, state)}
      </div>
    </div>
    ${lead ? '' : html`<div class="story-thumb">${coverArt(article, { variant: 'thumb' })}</div>`}
  </a>`;
}

function briefTemplate(state) {
  const cap = capProgress(state);
  const streak = streakDisplay(state);
  const daily = dailyQuizRecord(state);
  const today = new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'short' });
  const maxDaily = ECONOMY.dailyQuiz.questions * ECONOMY.dailyQuiz.coinsPerCorrect + ECONOMY.dailyQuiz.perfectBonus;
  const quizLine = daily?.completedAt
    ? html`<span class="grow"><b>Daily quiz done</b><span>${daily.correct}/${daily.total} correct · +${daily.coins} coins · new set tomorrow</span></span>`
    : html`<span class="grow"><b>Daily quiz</b><span>${ECONOMY.dailyQuiz.questions} questions · up to +${maxDaily} coins</span></span>`;
  return html`<section class="brief card card-ink" aria-label="Today's brief">
    <div class="brief-row">
      <div><div class="brief-kicker">Today's brief</div><div class="brief-date">${today}</div></div>
      <div class="brief-streak" data-testid="brief-streak" title="Reading streak">${icon('flame')}<span>${streak.current ? html`<b>${streak.current}</b>-day streak` : 'Start a streak'}</span></div>
    </div>
    <a class="brief-quiz ${daily?.completedAt ? 'is-done' : ''}" href="#/practice/daily" data-testid="brief-daily-quiz">
      <span class="brief-quiz-ico">${daily?.completedAt ? icon('check') : icon('target')}</span>${quizLine}${icon('chevron')}
    </a>
    <div class="brief-cap">
      <div class="row-between"><span>Activity coins today</span><span class="num" data-testid="cap-text"><b>${cap.earned}</b>/${cap.cap}</span></div>
      <div class="meter on-ink" role="progressbar" aria-label="Activity coins today" aria-valuemin="0" aria-valuemax="${cap.cap}" aria-valuenow="${cap.earned}"><span style="width:${(cap.ratio * 100).toFixed(1)}%"></span></div>
    </div>
  </section>`;
}

function skeletons() {
  return html`${[0, 1, 2, 3].map((i) => html`<div class="story card skeleton-card ${i === 0 ? 'story--lead' : 'story--compact'}" aria-hidden="true">
    ${i === 0 ? html`<div class="skeleton" style="aspect-ratio:16/9;border-radius:0"></div>` : ''}
    <div class="story-body"><div class="skeleton" style="height:12px;width:40%"></div><div class="skeleton" style="height:18px;width:90%"></div><div class="skeleton" style="height:12px;width:60%"></div></div>
    ${i === 0 ? '' : html`<div class="story-thumb skeleton"></div>`}
  </div>`)}`;
}

export function render(container, ctx) {
  const dispose = disposer();
  const state = getState();
  mount(container, html`<div class="feed page">
    <div class="feed-search" role="search">
      <div class="input-affix">
        ${icon('search')}
        <input type="search" class="input" placeholder="Search explainers, exams, facts" aria-label="Search articles" data-testid="feed-search" value="${feedState.query}" enterkeyhint="search">
        <button type="button" class="icon-btn clear-btn" aria-label="Clear search" data-action="clear" ${feedState.query ? '' : html`hidden`}>${icon('close')}</button>
      </div>
    </div>
    ${briefTemplate(state)}
    <div class="chips-row" role="group" aria-label="Filter by category">
      <button type="button" class="chip" data-action="cat" data-cat="all" data-testid="chip-all" aria-pressed="${feedState.category === 'all' ? 'true' : 'false'}">All</button>
      ${CATEGORIES.map((c) => html`<button type="button" class="chip" data-action="cat" data-cat="${c.key}" data-testid="chip-${c.key}" aria-pressed="${feedState.category === c.key ? 'true' : 'false'}" style="--cat:${c.color}"><span class="dot"></span>${c.short}</button>`)}
    </div>
    <div class="feed-list" id="feed-list" data-testid="feed-list">${skeletons()}</div>
    <p class="feed-foot">${icon('info')}<span>Demo content: original explainers written for this prototype. Publishers and sponsors are fictional.</span></p>
  </div>`);

  const listEl = container.querySelector('#feed-list');
  const searchEl = container.querySelector('input[type="search"]');
  const clearBtn = container.querySelector('[data-action="clear"]');

  // Ad impressions are counted when the user sees a list, not when another tab's change re-renders it.
  let countAds = !ctx.external;
  const renderList = () => {
    const count = countAds;
    countAds = true;
    const s = getState();
    const list = search(filterByCategory(rankFeed(s), feedState.category), feedState.query);
    if (!list.length) {
      mount(listEl, html`<div class="empty" data-testid="feed-empty">
        ${icon('search')}
        <div class="empty-title">No articles match "${feedState.query}"</div>
        <p>Try a different word, or clear the search${feedState.category !== 'all' ? ' and category filter' : ''}.</p>
        <button type="button" class="btn btn-secondary" data-action="reset-filters">Clear filters</button>
      </div>`);
      return;
    }
    const items = [];
    list.forEach((a, i) => {
      items.push(storyCard(a, i === 0 ? 'lead' : 'compact', s));
      if ((i + 1) % APP.feedAdEvery === 0 && i < list.length - 1) items.push(renderAdSlot({ placement: 'feed', count }));
    });
    const countLabel = feedState.query || feedState.category !== 'all' ? html`<p class="feed-count muted small" aria-live="polite">${fmtNumber(list.length)} ${list.length === 1 ? 'article' : 'articles'}</p>` : '';
    mount(listEl, html`${countLabel}${items}`);
  };

  // One frame of skeletons, then the (bundled) list.
  const raf = requestAnimationFrame(() => {
    renderList();
    if (feedState.scroll) container.scrollTop = feedState.scroll;
  });
  dispose.add(() => cancelAnimationFrame(raf));

  let debounce = null;
  const onSearch = () => {
    clearTimeout(debounce);
    debounce = setTimeout(() => {
      feedState.query = searchEl.value.trim().slice(0, 60);
      clearBtn.hidden = !searchEl.value;
      renderList();
    }, 150);
  };
  searchEl.addEventListener('input', onSearch);
  dispose.add(() => {
    searchEl.removeEventListener('input', onSearch);
    clearTimeout(debounce);
  });

  dispose.add(delegate(container, 'click', '[data-action]', (e, el) => {
    const action = el.dataset.action;
    if (action === 'cat') {
      feedState.category = el.dataset.cat;
      container.querySelectorAll('[data-action="cat"]').forEach((chip) => chip.setAttribute('aria-pressed', String(chip === el)));
      renderList();
    } else if (action === 'clear') {
      searchEl.value = '';
      feedState.query = '';
      clearBtn.hidden = true;
      renderList();
      searchEl.focus();
    } else if (action === 'reset-filters') {
      searchEl.value = '';
      feedState.query = '';
      feedState.category = 'all';
      clearBtn.hidden = true;
      container.querySelectorAll('[data-action="cat"]').forEach((chip) => chip.setAttribute('aria-pressed', String(chip.dataset.cat === 'all')));
      renderList();
    }
  }));

  dispose.add(() => {
    feedState.scroll = container.scrollTop;
  });
  return dispose;
}
