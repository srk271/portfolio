// Pure unit-economics model for the Partners & investors screen. Assumptions, not forecasts.
const num = (v) => Math.max(0, Number(v) || 0);
const frac = (v) => Math.min(1, num(v));

export function computeUnitEconomics(a) {
  const mau = num(a.mau);
  const readsPerUserPerDay = num(a.readsPerUserPerDay);
  const adImpressionsPerRead = num(a.adImpressionsPerRead);
  const ecpmRupees = num(a.ecpmRupees);
  const payoutPerVerifiedReadRupees = num(a.payoutPerVerifiedReadRupees);
  const brandFundedShare = frac(a.brandFundedShare);
  const premiumConversion = frac(a.premiumConversion);
  const premiumPriceRupees = num(a.premiumPriceRupees);

  const monthlyReads = mau * readsPerUserPerDay * 30;
  const adReads = monthlyReads * (1 - premiumConversion); // premium users see no ads
  const impressions = adReads * adImpressionsPerRead;
  const adRevenue = (impressions / 1000) * ecpmRupees;
  const premiumRevenue = mau * premiumConversion * premiumPriceRupees;
  const revenue = adRevenue + premiumRevenue;
  const rewardCost = monthlyReads * payoutPerVerifiedReadRupees * (1 - brandFundedShare);
  const grossMargin = revenue - rewardCost;
  const marginPct = revenue > 0 ? grossMargin / revenue : 0;
  const perUser = mau > 0 ? grossMargin / mau : 0;
  return {
    monthlyReads, adReads, impressions, adRevenue, premiumRevenue, revenue, rewardCost,
    grossMargin, marginPct, perUser, annualRevenue: revenue * 12, annualMargin: grossMargin * 12
  };
}
