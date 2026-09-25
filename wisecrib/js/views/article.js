// Article reader: hero, byline with licensing, key facts, body, verified-read tracker card, quiz CTA, related.
import { html, mount, delegate, disposer } from '../dom.js';
import { emit } from '../bus.js';
import { getState } from '../store.js';
import { ECONOMY } from '../config.js';
import { getArticle } from '../data/articles.js';
import { getCategory } from '../data/categories.js';
import { fmtDate, fmtClock, fmtNumber } from '../format.js';
import { icon, coinIcon } from '../ui/icons.js';
import { coverArt } from '../ui/artwork.js';
import { renderAdSlot } from '../ui/adslot.js';
import { toast } from '../ui/toast.js';
import { createReadTracker, recordVerifiedRead, requiredDwellMs, readCoinsFor, markOpened } from '../services/reading.js';
import { quizCoinsFor } from '../services/quiz.js';
import { relatedArticles } from '../services/recommend.js';
import { reasonText } from '../services/wallet.js';
import { storyCard } from './feed.js';

const RING_R = 18;
const RING_C = 2 * Math.PI * RING_R;

export function licenseLabel(source) {
  switch (source.licenseType) {
    case 'licensed': return `Partner content · licensed from ${source.publisherName}`;
    case 'syndicated': return `Syndicated from ${source.publisherName}`;
    default: return `Original explainer by ${source.publisherName}`;
  }
}

function premiumReadCoins() {
  return Math.ceil(ECONOMY.read.baseCoins * ECONOMY.premium.multiplier);
}

function statusReading(required, coins) {
  return html`<div class="rs-head"><span class="rs-title">${icon('shield')}Verified read</span><span class="reward-badge">${coinIcon()}+${coins}</span></div>
    <ul class="rs-rows">
      <li class="rs-row" data-row="end"><span class="rs-check">${icon('circle')}</span><span class="grow">Reached the end</span><span class="rs-val" data-end-text>Not yet</span></li>
      <li class="rs-row" data-row="time">
        <span class="ring" role="progressbar" aria-label="Active reading time" aria-valuemin="0" aria-valuemax="100" aria-valuenow="0" data-ring>
          <svg viewBox="0 0 44 44"><circle class="ring-track" cx="22" cy="22" r="${RING_R}" fill="none" stroke-width="5"/><circle class="ring-fill" cx="22" cy="22" r="${RING_R}" fill="none" stroke-width="5" stroke-linecap="round" stroke-dasharray="${RING_C.toFixed(2)}" stroke-dashoffset="${RING_C.toFixed(2)}"/></svg>
        </span>
        <span class="grow">Active reading time</span><span class="rs-val num" data-time>0:00 / ${fmtClock(required)}</span>
      </li>
    </ul>
    <p class="rs-copy">Verified reading earns +${ECONOMY.read.baseCoins} coins (premium +${premiumReadCoins()}). Read to the end with the page open; time only counts while it's visible.</p>`;
}

function statusRewarded(coins, quizCoins) {
  return html`<div class="rs-done is-success">${icon('checkCircle')}<div><b>Verified read · +${coins} coins</b><span>Added to your wallet. Take the quiz for up to +${quizCoins} more.</span></div></div>`;
}

function statusRejected(reason) {
  return html`<div class="rs-done is-warning">${icon('alert')}<div><b>Verified read · reward not applied</b><span>${reasonText(reason)}. Logged in your <a href="#/wallet">wallet</a> so you can see why.</span></div></div>`;
}

function statusAlready(read) {
  return html`<div class="rs-done is-muted">${icon('checkCircle')}<div><b>You've already earned this article</b><span>${read.coins > 0 ? `Verified earlier for +${read.coins} coins.` : `Verified earlier · reward not applied (${reasonText(read.reason).toLowerCase()}).`} Each article pays once.</span></div></div>`;
}

function quizCta(article, state) {
  const read = state.reads[article.id];
  const quiz = state.quizzes[article.id];
  const coins = quizCoinsFor(state);
  const bonus = article.sponsor?.quizBonus;
  const reward = html`+${coins} coins${bonus ? html` <span class="quiz-cta-bonus">+${bonus} brand bonus</span>` : ''}`;
  if (!read) {
    return html`<button type="button" class="quiz-cta card is-locked" data-testid="quiz-cta" data-state="locked" disabled>
      <span class="quiz-cta-ico">${icon('lock')}</span>
      <span class="grow"><span class="quiz-cta-title">Take the ${ECONOMY.articleQuiz.questions}-question quiz · ${reward}</span><span class="quiz-cta-sub">Unlocks after a verified read</span></span>
    </button>`;
  }
  if (quiz?.rewardedAttemptAt) {
    return html`<a class="quiz-cta card is-done" href="#/quiz/${article.id}" data-testid="quiz-cta" data-state="done">
      <span class="quiz-cta-ico">${icon('checkCircle')}</span>
      <span class="grow"><span class="quiz-cta-title">Quiz: ${quiz.correct}/${quiz.total} · ${quiz.coins + quiz.sponsorCoins > 0 ? `+${quiz.coins + quiz.sponsorCoins} coins` : 'no coins'}</span><span class="quiz-cta-sub">Practice again (no coins)</span></span>
      ${icon('chevron')}
    </a>`;
  }
  return html`<a class="quiz-cta card is-ready" href="#/quiz/${article.id}" data-testid="quiz-cta" data-state="ready">
    <span class="quiz-cta-ico">${icon('target')}</span>
    <span class="grow"><span class="quiz-cta-title">Take the ${ECONOMY.articleQuiz.questions}-question quiz · ${reward}</span><span class="quiz-cta-sub">One rewarded attempt · pass ${ECONOMY.articleQuiz.passMark}/${ECONOMY.articleQuiz.questions}</span></span>
    ${icon('chevron')}
  </a>`;
}

// The read tracker keeps running when another tab changes the state; the award re-checks the latest state.
export const keepOnExternalChange = true;

export function render(container, ctx) {
  const dispose = disposer();
  const article = getArticle(ctx.params.id);
  if (!article) {
    ctx.setTitle('Not found');
    mount(container, html`<div class="page"><div class="empty card" data-testid="article-not-found">
      ${icon('search')}<div class="empty-title">Article not found</div>
      <p>It may have been removed from this demo.</p><a class="btn btn-primary" href="#/feed">Back to feed</a>
    </div></div>`);
    return null;
  }
  const cat = getCategory(article.category);
  ctx.setTitle(cat.short);
  markOpened(article.id);

  const state = getState();
  const existing = state.reads[article.id];
  const required = requiredDwellMs(article);
  const coins = readCoinsFor(state);
  const initialState = existing ? 'already' : 'reading';

  mount(container, html`<article class="reader" data-article-id="${article.id}">
    <div class="reader-hero">${coverArt(article, { variant: 'hero' })}</div>
    <div class="reader-main page">
      <div class="story-tags">
        <span class="tag tag-cat" style="--cat:${cat.color}">${cat.label}</span>
        <span class="tag tag-demo">Demo content</span>
        ${article.sponsor ? html`<span class="tag tag-sponsored">Sponsored</span>` : ''}
      </div>
      <h1 class="reader-title">${article.title}</h1>
      <p class="reader-dek">${article.dek}</p>
      <div class="byline">
        <div class="byline-main">
          <span class="byline-pub">${article.source.publisherName}</span>
          <span class="byline-license" data-testid="license-label">${licenseLabel(article.source)}</span>
        </div>
        <a class="byline-original" href="${article.source.sourceUrl}" data-action="original">Read original ${icon('external')}</a>
      </div>
      <div class="reader-meta">${fmtDate(article.publishedAt)} · ${article.readingTime} min read · ${fmtNumber(article.wordCount)} words · ${article.author}</div>
      <div class="exam-tags">${article.examTags.map((t) => html`<span class="tag tag-exam">${t}</span>`)}</div>
      ${article.sponsor ? html`<div class="sponsor-banner" data-testid="sponsor-banner">${icon('sparkle')}<span>Sponsored by <b>${article.sponsor.name}</b> · quiz bonus +${article.sponsor.quizBonus} coins is brand-funded</span></div>` : ''}
      <aside class="keyfacts" aria-labelledby="kf-title">
        <h2 id="kf-title">${icon('bolt')}Key facts</h2>
        <ul>${article.keyFacts.map((f) => html`<li>${f}</li>`)}</ul>
      </aside>
      <div class="reader-text">${article.body.map((p) => html`<p>${p}</p>`)}</div>
      <div id="article-end" class="article-end" aria-hidden="true"></div>
      <section class="read-status card" data-testid="read-status" data-required-ms="${required}" data-state="${initialState}" aria-live="polite">
        ${existing ? statusAlready(existing) : statusReading(required, coins)}
      </section>
      ${renderAdSlot({ placement: 'article' })}
      <div id="quiz-cta-slot">${quizCta(article, state)}</div>
      <section class="related">
        <h2 class="h-section">More in ${cat.label}</h2>
        <div class="related-list">${relatedArticles(article, state).map((a) => storyCard(a, 'compact', state))}</div>
      </section>
      <footer class="demo-note reader-foot" data-testid="demo-footer">${icon('info')}<span>Demo content: an original explainer written for this prototype. Publisher names are fictional; "Read original" is a placeholder.</span></footer>
    </div>
  </article>`);

  const statusEl = container.querySelector('[data-testid="read-status"]');
  const ctaSlot = container.querySelector('#quiz-cta-slot');

  dispose.add(delegate(container, 'click', '[data-action="original"]', (e) => {
    e.preventDefault();
    toast({ message: 'Placeholder: original source link', kind: 'info', iconName: 'external' });
  }));

  if (!existing) {
    const ringFill = statusEl.querySelector('.ring-fill');
    const ringEl = statusEl.querySelector('[data-ring]');
    const timeEl = statusEl.querySelector('[data-time]');
    const endRow = statusEl.querySelector('[data-row="end"]');
    let lastSecond = -1;
    let endShown = false;

    const tracker = createReadTracker({
      article,
      sentinelEl: container.querySelector('#article-end'),
      scrollEl: container,
      onProgress: ({ activeMs, requiredMs, reachedEnd, ratio }) => {
        const sec = Math.floor(Math.min(activeMs, requiredMs) / 1000);
        if (sec !== lastSecond) {
          lastSecond = sec;
          timeEl.textContent = `${fmtClock(Math.min(activeMs, requiredMs))} / ${fmtClock(requiredMs)}`;
          ringEl.setAttribute('aria-valuenow', String(Math.round(ratio * 100)));
        }
        ringFill.style.strokeDashoffset = String((RING_C * (1 - ratio)).toFixed(2));
        if (ratio >= 1) ringEl.classList.add('is-done');
        if (reachedEnd && !endShown) {
          endShown = true;
          endRow.classList.add('is-done');
          mount(endRow.querySelector('.rs-check'), html`${icon('checkCircle')}`);
          endRow.querySelector('[data-end-text]').textContent = 'Done';
        }
      },
      onVerified: ({ dwellMs }) => {
        const result = recordVerifiedRead(article.id, dwellMs);
        const now = getState();
        if (result.alreadyRewarded) {
          statusEl.dataset.state = 'already';
          mount(statusEl, statusAlready(result));
        } else if (result.coins > 0) {
          statusEl.dataset.state = 'rewarded';
          mount(statusEl, statusRewarded(result.coins, quizCoinsFor(now)));
          emit('coins:awarded', {
            amount: result.coins,
            from: statusEl.getBoundingClientRect(),
            message: result.capped ? `+${result.coins} coins (daily cap reached)` : `Verified read · +${result.coins} coins`
          });
        } else {
          statusEl.dataset.state = 'rejected';
          mount(statusEl, statusRejected(result.reason));
          emit('reward:rejected', { reason: result.reason, message: `Reward not applied — ${reasonText(result.reason)}` });
        }
        mount(ctaSlot, quizCta(article, now));
        if (result.referralPaid) {
          setTimeout(() => {
            emit('coins:awarded', { amount: ECONOMY.referral.reward, message: `Referral complete · +${ECONOMY.referral.reward} coins` });
          }, 1400);
        }
      }
    });
    dispose.add(() => tracker.destroy());
  }
  return dispose;
}
