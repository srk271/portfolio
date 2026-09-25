// "How rewards work": every number here is read from config, so the copy always matches the rules.
import { html, mount } from '../dom.js';
import { getState } from '../store.js';
import { ECONOMY } from '../config.js';
import { ARTICLES } from '../data/articles.js';
import { fmtRupees, fmtPercent, fmtCoins } from '../format.js';
import { icon, coinIcon } from '../ui/icons.js';

export function render(container) {
  const e = ECONOMY;
  const m = e.premium.multiplier;
  const readP = Math.ceil(e.read.baseCoins * m);
  const quizP = Math.ceil(e.articleQuiz.passCoins * m);
  const dailyMax = e.dailyQuiz.questions * e.dailyQuiz.coinsPerCorrect + e.dailyQuiz.perfectBonus;
  const perMonth = (cap) => fmtRupees((cap * 30) / e.coinsPerRupee, { decimals: 0 });
  const onboarded = getState().profile.onboarded;
  const sponsorBonus = Math.max(0, ...ARTICLES.filter((a) => a.sponsor).map((a) => a.sponsor.quizBonus));

  const rows = [
    ['Verified read', `+${e.read.baseCoins}`, `+${readP}`, 'Once per article', true],
    [`Article quiz (pass ${e.articleQuiz.passMark}/${e.articleQuiz.questions})`, `+${e.articleQuiz.passCoins}`, `+${quizP}`, 'One rewarded attempt; retries are practice', true],
    ['Sponsored quiz bonus', `+${sponsorBonus}`, `+${sponsorBonus}`, 'Brand-funded, on top of the quiz reward', false],
    ['Daily current-affairs quiz', `+${e.dailyQuiz.coinsPerCorrect}/correct, +${e.dailyQuiz.perfectBonus} perfect`, 'same', `Once a day, up to +${dailyMax}`, true],
    ['Daily mock test', '—', `+${e.mockTest.coinsPerCorrect}/correct`, `Premium, ${e.mockTest.questions} questions, once a day`, true],
    ['Sponsored video (opt-in)', `+${e.rewardedVideo.coins}`, 'ad-free', `Max ${e.rewardedVideo.maxPerDay} a day`, true],
    ['Daily bonus', `${e.dailyBonus.base} + ${e.dailyBonus.perStreakDay}/streak day, max ${e.dailyBonus.maxFree}`, `${e.dailyBonus.premiumMultiplier}×, max ${e.dailyBonus.maxFree * e.dailyBonus.premiumMultiplier}`, 'Once a day', false],
    ['Referral', `+${e.referral.reward}`, `+${e.referral.reward}`, `After ${e.referral.requiredVerifiedReads} verified reads`, false],
    ['Welcome bonus', `+${e.welcomeBonus}`, `+${e.welcomeBonus}`, 'Once', false]
  ];

  mount(container, html`<div class="page how" data-testid="how-rewards">
    <header class="page-head">
      <h1 class="h-display" data-large-title>How rewards work</h1>
      <p class="muted">Small, capped rewards for reading carefully. Every number on this page comes straight from the app's rules.</p>
    </header>
    <p class="card-note is-warning">${icon('info')}<span><strong>Demo prototype.</strong> Coins, payouts, vouchers and payments are simulated. No real money moves.</span></p>

    <section class="card card-pad stack-sm">
      <h2 class="h-section">Earning</h2>
      <div class="table-wrap">
        <table class="earn-table">
          <thead><tr><th scope="col">Activity</th><th scope="col">Free</th><th scope="col">Premium</th></tr></thead>
          <tbody>${rows.map(([a, f, p, note, capped]) => html`<tr><th scope="row">${a}<span class="earn-note">${note}${capped ? ' · counts toward cap' : ''}</span></th><td class="num">${f}</td><td class="num">${p}</td></tr>`)}</tbody>
        </table>
      </div>
    </section>

    <section class="card card-pad stack-sm">
      <h2 class="h-section">Coin value</h2>
      <p class="coin-value">${coinIcon()}<span><b>${e.coinsPerRupee} coins = ₹1</b> (1 coin = 1 paisa). The most anyone can earn from activity is about ${perMonth(e.dailyCap.free)} a month on the free plan and ${perMonth(e.dailyCap.premium)} on Premium. It's a reward for learning, not an income.</span></p>
    </section>

    <section class="card card-pad stack-sm">
      <h2 class="h-section">What funds rewards</h2>
      <ul class="bullets">
        <li><b>Ad revenue share.</b> About ${fmtPercent(e.funding.adRevenueShareToRewards)} of ad revenue from free readers goes into the reward pool.</li>
        <li><b>Brand-funded rewards.</b> Partners fund sponsored-quiz bonuses and part of voucher values ("Partner-funded" badge).</li>
        <li><b>Premium.</b> Subscriptions pay for tools like mock tests and fee-free redemptions.</li>
      </ul>
    </section>

    <section class="card card-pad stack-sm">
      <h2 class="h-section">Fees and minimums</h2>
      <dl class="kv">
        <div class="kv-row"><dt>Free · cash (UPI)</dt><dd>min ${fmtRupees(e.redemption.free.minRupees, { decimals: 0 })} · ${fmtPercent(e.redemption.free.feeRate)} fee</dd></div>
        <div class="kv-row"><dt>Premium · cash (UPI)</dt><dd>min ${fmtRupees(e.redemption.premium.minRupees, { decimals: 0 })} · ${fmtPercent(e.redemption.premium.feeRate)} fee</dd></div>
        <div class="kv-row"><dt>Vouchers</dt><dd>Free ${fmtPercent(e.redemption.free.feeRate)} fee in coins · Premium ${fmtPercent(e.redemption.premium.feeRate)}</dd></div>
        <div class="kv-row"><dt>Charity donations</dt><dd>${fmtPercent(e.redemption.charityFeeRate)} fee for everyone</dd></div>
      </dl>
      <p class="muted small">Everyone can redeem. You always see coins → value → fee → what you receive before confirming. Example: ₹50 on the free plan = ${fmtCoins(50 * e.coinsPerRupee)} coins, ${fmtRupees(50 * e.redemption.free.feeRate)} fee, you receive ${fmtRupees(50 * (1 - e.redemption.free.feeRate))}.</p>
    </section>

    <section class="card card-pad stack-sm">
      <h2 class="h-section">Caps and limits</h2>
      <ul class="bullets">
        <li><b>Daily activity cap:</b> ${e.dailyCap.free} coins (Premium ${e.dailyCap.premium}) from reads, quizzes, mock tests and sponsored videos. Bonuses, referrals and brand-funded rewards don't count.</li>
        <li><b>Velocity limit:</b> at most ${e.read.maxRewardedPer10Min} rewarded reads in any 10 minutes. Extra reads still count as read, without coins.</li>
        <li><b>One reward per article</b> and <b>one rewarded quiz attempt</b> per article.</li>
        <li><b>Referral:</b> pays only after ${e.referral.requiredVerifiedReads} verified reads.</li>
        <li>Blocked rewards appear in your wallet with the reason, so nothing is hidden.</li>
      </ul>
    </section>

    <section class="card card-pad stack-sm">
      <h2 class="h-section">What counts as a verified read</h2>
      <p>You scroll to the end of the article <b>and</b> spend active reading time of at least ${e.read.minDwellSeconds} seconds or ${fmtPercent(e.read.dwellFraction)} of the article's expected reading time (at ${e.read.wpm} words a minute), whichever is longer. Time only counts while the page is visible.</p>
    </section>

    <section class="card card-pad stack-sm" data-testid="ads-policy">
      <h2 class="h-section">Ads</h2>
      <p>Banner ads never earn coins. Only the clearly labeled opt-in sponsored video, in the AdMob Rewarded Ads format, can (+${e.rewardedVideo.coins}, max ${e.rewardedVideo.maxPerDay} a day). Premium is ad-free.</p>
    </section>

    <section class="card card-pad stack-sm">
      <h2 class="h-section">Anti-fraud</h2>
      <ul class="bullets">
        <li>Verified reading, velocity limits, daily caps and single rewarded attempts.</li>
        <li>A one-time OTP check of your UPI ID and phone before the first cash payout.</li>
        <li>A transparent ledger, including rewards that were not applied.</li>
      </ul>
      <p class="muted small">In the real app these checks run on our servers, with account and device signals, server-side timers, fraud scoring and payout KYC. This prototype runs them in your browser only to demonstrate the rules.</p>
    </section>

    <p class="demo-note">${icon('info')}<span>Content is demo content written for this prototype. Publisher and brand names are fictional.</span></p>
    ${onboarded ? '' : html`<a class="btn btn-primary btn-block" href="#/onboarding">Back to sign-up</a>`}
  </div>`);
  return null;
}
