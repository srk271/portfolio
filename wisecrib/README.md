# WiSeCRiB

**Read the news. Prove it with a quiz. Earn rewards.**

WiSeCRiB is a current-affairs reader for Indian exam aspirants (UPSC and State PSC, SSC, Banking, Railways) and curious general readers. You read a short explainer, the app verifies that you actually read it, a three-question quiz checks what stuck, and both earn small coins you can redeem for vouchers or a UPI payout.

This folder is a **front-end prototype** of the WiSeCRiB business plan: an installable, mobile-first PWA built with plain HTML, CSS and JavaScript modules. There is no build step and no runtime dependency. Everything runs in the browser and is stored in `localStorage`. Content, rewards, payments and payouts are all **simulated**.

## Run it

From the repository root:

```bash
python3 -m http.server 8080
```

Then open <http://localhost:8080/wisecrib/>. All paths are relative, so it also works under a subpath such as GitHub Pages (`https://<user>.github.io/portfolio/wisecrib/`).

To install it as an app, use the browser's install button (desktop) or **Add to Home screen** (Android/iOS). Settings also shows an Install button when the browser offers one. After the first visit the service worker caches the app shell, so it keeps working offline.

## Structure

```
wisecrib/
  index.html          App shell, theme boot script, manifest + icon links
  manifest.json       Web app manifest (relative paths, maskable icons, shortcuts)
  sw.js               Service worker: versioned precache, offline navigation fallback
  icons/              SVG masters + generated PNGs (192, 512, maskable, apple-touch)
  tools/make-icons.mjs  One-off dev script that renders the PNG icons (not shipped to users)
  css/                tokens.css, base.css, components.css, views.css
  js/
    main.js           Boot: store, theme, header/tabbar, router, service worker, global events
    config.js         Every economic rule and constant (see below)
    router.js         Hash router, route table, onboarding guard
    store.js          State schema, load/migrate/validate, update/subscribe, reset
    storage.js        Namespaced localStorage adapter with an in-memory fallback
    bus.js, dom.js, format.js, pwa.js
    services/         wallet, reading, quiz, recommend, premium, bonus, referral,
                      payout, redeem, stats, economics, profile
    ui/               header, tabbar, toast, coinburst, sheet, adslot, quizEngine, icons, artwork
    views/            onboarding, feed, article, quiz, practice, wallet, redeem, premium,
                      profile, settings, partners, howRewards, notfound
    data/             categories, goals, articles (28), vouchers, partners
```

Services own all domain logic and are the only modules that write to the store. Each keeps a small, stable signature (`recordVerifiedRead`, `submitArticleQuiz`, `redeemCash`, ...) so a real backend can replace the localStorage mutations later without touching the views.

## Demo values

Everything lives in `js/config.js`, and the **How rewards work** screen is generated from it, so the copy always matches the rules.

| Rule | Value |
| --- | --- |
| Coin value | 100 coins = ₹1 (1 coin = 1 paisa) |
| Welcome bonus | 100 coins |
| Verified read | +3 coins (Premium +5), once per article |
| Verified read definition | reached the end **and** active dwell ≥ max(20 s, 40% of expected reading time at 200 wpm) |
| Article quiz | 3 questions, pass 2/3 for +5 (Premium +8); one rewarded attempt, retries are practice |
| Sponsored quiz bonus | +5, brand-funded, outside the daily cap |
| Daily quiz | 8 questions, +1 per correct, +2 for a perfect score, once a day |
| Mock test (Premium) | 15 questions, 10-minute timer (display only), +1 per correct, once a day |
| Daily activity cap | 60 coins free, 90 Premium (reads, quizzes, mock test, sponsored video) |
| Velocity limit | max 3 rewarded reads in any 10 minutes |
| Daily bonus | 3 + 1 per streak day, max 10 (Premium 2×, max 20) |
| Sponsored video (opt-in) | +2 coins, max 3 a day, free users only |
| Referral | +50 after the referred reader's 3 verified reads |
| Cash redemption (UPI) | Free: min ₹50, 10% fee. Premium: min ₹20, 0% fee |
| Voucher fee | Free 10% (in coins), Premium 0%, charity 0% for everyone |
| Premium | ₹49/month or ₹399/year (simulated checkout) |
| Unit-economics defaults | 100k MAU, 3 reads/day, 2 impressions/read, ₹30 eCPM, ₹0.08 reward/read, 30% brand-funded, 3% premium at ₹49 |

Content lives in `js/data/`. The 28 articles are original, evergreen explainers written for this prototype; event-style stories are set in the fictional Republic of Veridia, and publisher and sponsor names are fictional.

**Local testing knob.** On `localhost` or `127.0.0.1` only, a JSON object in `localStorage['wisecrib:dev-config']` is deep-merged over the economy config, for example to shorten the dwell time:

```js
localStorage.setItem('wisecrib:dev-config', JSON.stringify({ read: { minDwellSeconds: 2, dwellFraction: 0.01 } }));
```

It is ignored on any deployed host.

## What is simulated

Rewards and the coin ledger, the Premium checkout, UPI payouts and the one-time OTP check (the demo OTP is shown on screen and never stored), voucher codes, referrals, ads (labeled placeholders for Google AdMob), the opt-in sponsored video, the weekly digest, "Read original" links and the **Add 10,000 demo coins** tool (labeled "not earnable" in the ledger).

## Risk mitigations built into the prototype

| Risk | How the prototype handles it |
| --- | --- |
| Reward economics | Small rewards funded by an ad-revenue share and brand partners; a hard daily cap; brand-funded vouchers and sponsored quizzes; Premium sold on tools and fairness, not income (max ≈ ₹27/month even on Premium) |
| Fraud | Verified reading (scroll + visible dwell time), velocity limit, one reward per article, one rewarded quiz attempt, referral pays only after real activity, OTP check before the first payout, and a ledger that shows rejected rewards with the reason |
| Fair redemption | Everyone can redeem; tiers only change the minimum and fee; the full coins → value → fee → receive breakdown is shown before every confirmation |
| Ad policy | Banner ads never earn coins; only the clearly labeled opt-in sponsored video (AdMob Rewarded format) does; Premium is ad-free |
| Content licensing | Every article carries source, publisher and licence fields (licensed, syndicated or original), shown in the byline; all demo content is original |

## Anti-fraud disclaimer

The checks in this prototype run in the browser only to demonstrate the rules. They are easy to bypass on purpose. In a real app they belong on the server: authenticated accounts, device and account signals, server-side read timers, fraud scoring, payout KYC and a server-owned ledger.

## Verification

The app was checked end to end with Playwright against `python3 -m http.server`. The script lives outside the repository; to reproduce it, install Playwright in a scratch folder (`npm i playwright`) and drive the flow: onboarding with a referral code, a real-rule verified read, article quiz and practice retry, velocity and daily-cap rejections in the ledger, daily quiz and bonus, free redemption with the 10% fee and the OTP gate, Premium checkout and 0% fee redemption, referral pending → paid, the partners calculator, dark theme, reset, service worker registration, manifest validity, and an offline reload with the server stopped. The same script asserts zero console errors and no horizontal overflow at 320, 390 and 1440 px widths.

To regenerate the PNG icons after editing the SVGs:

```bash
NODE_PATH=/path/to/node_modules node wisecrib/tools/make-icons.mjs
```

## Next steps toward iOS, Android and a backend

- Wrap the PWA with Capacitor (or build native shells) for the Play Store and App Store.
- An API for content, the ledger, quizzes and redemptions, with server-side read verification.
- Real authentication, payout KYC and a licensed payment or voucher provider.
- AdMob SDK integration (banner and rewarded formats) with a transparent revenue share into the reward pool.
- A publisher licensing pipeline in place of the demo articles.
- Analytics reported as privacy-safe aggregates only.
