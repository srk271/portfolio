// Publishers, brand partners, revenue streams and milestones (from the business plan). All names are fictional.
export const PUBLISHERS = Object.freeze([
  { id: 'civic-ledger', name: 'Civic Ledger', licenseType: 'licensed', focus: 'Polity, governance and public policy explainers' },
  { id: 'meridian-wire', name: 'Meridian Wire', licenseType: 'syndicated', focus: 'Economy, world affairs and sport via syndication' },
  { id: 'northline-science', name: 'Northline Science', licenseType: 'licensed', focus: 'Science, health and climate' },
  { id: 'wisecrib-desk', name: 'WiSeCRiB Desk', licenseType: 'original', focus: 'Original exam-focused explainers' }
]);

export const BRAND_PARTNERS = Object.freeze([
  { id: 'prepsprint', name: 'PrepSprint', role: 'Sponsored quizzes and mock-test vouchers' },
  { id: 'airwave', name: 'AirWave Mobile', role: 'Recharge and data vouchers (part-funded)' },
  { id: 'pageturn', name: 'PageTurn Books', role: 'E-book credit and a sponsored explainer' },
  { id: 'inkwell', name: 'InkWell Stationery', role: 'Stationery vouchers' },
  { id: 'sikshalaya', name: 'Sikshalaya Education Fund', role: 'Charity redemptions with 0% fee' }
]);

export const REVENUE_STREAMS = Object.freeze({
  launch: [
    {
      key: 'ads', title: 'In-app ads via AdMob', icon: 'megaphone',
      body: 'Banner and native placements in the feed and reader for free users. Premium is ad-free. Banner ads never pay coins; a share of ad revenue funds the reward pool.'
    },
    {
      key: 'premium', title: 'Premium membership', icon: 'crown',
      body: '₹49/month or ₹399/year for 1.5× rewards, a higher daily cap, 0% redemption fee, mock tests and no ads.'
    }
  ],
  later: [
    'Sponsored quizzes',
    'Brand-funded rewards',
    'Publisher engagement insights (privacy-safe aggregates only)',
    'Affiliate study resources',
    'Partner reward programmes'
  ]
});

export const MILESTONES = Object.freeze([
  { when: '0–3 months', title: 'Beta launch', body: 'Public beta with early adopters to test verified reading, quizzes and redemptions.' },
  { when: '3–6 months', title: 'Build the user base', body: 'Social and influencer campaigns aimed at exam aspirants; referral programme live.' },
  { when: '6–12 months', title: 'Android and iOS apps', body: 'Native shells around the web app, AdMob SDK, real auth and payout KYC.' },
  { when: '6–12 months', title: 'More partners', body: 'More licensed publishers and brand-funded vouchers; first sponsored quizzes.' },
  { when: '12–18 months', title: 'New markets', body: 'Regional-language editions and expansion across Asia, with local partners.' },
  { when: '18–24 months', title: 'Monetisation and growth', body: 'Diversify revenue: sponsorships, publisher insights, partner reward programmes.' },
  { when: '24–36 months', title: 'Scale up', body: 'Invest in technology, the team and the brand.' }
]);
