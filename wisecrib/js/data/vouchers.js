// Brand-funded voucher catalog (fictional brands). "Partner-funded" when coins < face value in paise.
export const VOUCHERS = Object.freeze([
  {
    id: 'recharge-20', brand: 'AirWave Mobile', partnerId: 'airwave', title: '₹20 mobile recharge',
    faceValueRupees: 20, coins: 2000, category: 'recharge', premiumOnly: false, feeExempt: false,
    description: 'Prepaid top-up on any AirWave number.', gradient: ['#1257B0', '#42A5F5']
  },
  {
    id: 'data-1gb', brand: 'AirWave Mobile', partnerId: 'airwave', title: '1 GB data day pack',
    faceValueRupees: 19, coins: 1500, category: 'data', premiumOnly: false, feeExempt: false,
    description: '1 GB high-speed data, valid 24 hours. Part-funded by AirWave.', gradient: ['#0B7A5E', '#2BC48A']
  },
  {
    id: 'mocktest-5', brand: 'PrepSprint', partnerId: 'prepsprint', title: '5 full mock tests',
    faceValueRupees: 99, coins: 4000, category: 'learning', premiumOnly: false, feeExempt: false,
    description: 'Timed mocks with solutions for SSC, Banking or Railways.', gradient: ['#3F3DD8', '#7C4DFF']
  },
  {
    id: 'ebook-50', brand: 'PageTurn Books', partnerId: 'pageturn', title: '₹50 e-book credit',
    faceValueRupees: 50, coins: 5000, category: 'books', premiumOnly: false, feeExempt: false,
    description: 'Spend on any title in the PageTurn exam-prep store.', gradient: ['#6D4C41', '#C08552']
  },
  {
    id: 'stationery-50', brand: 'InkWell Stationery', partnerId: 'inkwell', title: '₹50 stationery voucher',
    faceValueRupees: 50, coins: 5000, category: 'stationery', premiumOnly: false, feeExempt: false,
    description: 'Notebooks, pens and highlighters for revision season.', gradient: ['#D84E00', '#FFB300']
  },
  {
    id: 'charity-10', brand: 'Sikshalaya Education Fund', partnerId: 'sikshalaya', title: '₹10 donation',
    faceValueRupees: 10, coins: 1000, category: 'charity', premiumOnly: false, feeExempt: true,
    description: 'Goes in full towards study material for first-generation learners.', gradient: ['#C62828', '#FF7A59']
  },
  {
    id: 'premium-mock-30d', brand: 'PrepSprint', partnerId: 'prepsprint', title: '30 days unlimited mocks',
    faceValueRupees: 199, coins: 6000, category: 'learning', premiumOnly: true, feeExempt: false,
    description: 'Unlimited PrepSprint mock tests for a month. Partner-funded.', gradient: ['#26248A', '#5856E6']
  },
  {
    id: 'premium-recharge-50', brand: 'AirWave Mobile', partnerId: 'airwave', title: '₹50 mobile recharge',
    faceValueRupees: 50, coins: 4500, category: 'recharge', premiumOnly: true, feeExempt: false,
    description: 'Prepaid top-up, part-funded by AirWave for Premium members.', gradient: ['#1A1960', '#42A5F5']
  }
].map(Object.freeze));

export const VOUCHER_MAP = Object.freeze(Object.assign(Object.create(null), Object.fromEntries(VOUCHERS.map((v) => [v.id, v]))));

export function getVoucher(id) {
  return typeof id === 'string' && Object.hasOwn(VOUCHER_MAP, id) ? VOUCHER_MAP[id] : null;
}
