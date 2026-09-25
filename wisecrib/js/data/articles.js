// 28 ORIGINAL demo articles written for this prototype. They are evergreen explainers of
// well-established general-knowledge topics; anything event-like is set in the fictional
// "Republic of Veridia". No real living people are quoted or described. Publishers and
// sponsors are fictional. Every card and reader labels this as demo content.
//
// Shape: { id, category, title, dek, source, author, publishedAt, examTags, keyFacts, body,
//          sponsor, quiz: [{ q, options, answer, factRef }] } + wordCount, readingTime (added below).
// keyFacts are the source of truth for quiz questions (factRef points into keyFacts).
import { ECONOMY, APP } from '../config.js';

const PUB = {
  civic: { partnerId: 'civic-ledger', publisherName: 'Civic Ledger', licenseType: 'licensed', sourceUrl: '#' },
  meridian: { partnerId: 'meridian-wire', publisherName: 'Meridian Wire', licenseType: 'syndicated', sourceUrl: '#' },
  northline: { partnerId: 'northline-science', publisherName: 'Northline Science', licenseType: 'licensed', sourceUrl: '#' },
  desk: { partnerId: 'wisecrib-desk', publisherName: 'WiSeCRiB Desk', licenseType: 'original', sourceUrl: '#' }
};

const AUTHOR = {
  civic: 'Civic Ledger Explainers',
  meridian: 'Meridian Wire Desk',
  northline: 'Northline Science Team',
  desk: 'WiSeCRiB Explainer Desk'
};

const SPONSOR = {
  prepsprint: { name: 'PrepSprint', tagline: 'Mock tests for every exam', quizBonus: 5 },
  airwave: { name: 'AirWave Mobile', tagline: 'Data that keeps up with you', quizBonus: 5 },
  pageturn: { name: 'PageTurn Books', tagline: 'Exam-prep e-books', quizBonus: 5 }
};

// Dates are fixed relative to APP.contentAnchor (24 Sep 2026) so recency ranking is stable.
const [AY, AM, AD] = APP.contentAnchor.slice(0, 10).split('-').map(Number);
function ago(days, time = '07:30') {
  const d = new Date(Date.UTC(AY, AM - 1, AD - days));
  return `${d.toISOString().slice(0, 10)}T${time}:00+05:30`;
}

const src = (key) => ({ ...PUB[key] });

const RAW = [
  // ---------------------------------------------------------------- Polity
  {
    id: 'polity-bill-to-law',
    category: 'polity',
    title: `How a bill becomes a law: the three readings explained`,
    dek: `Every central law passes through the same sequence of readings, committees and votes. Here is the path, step by step.`,
    source: src('civic'), author: AUTHOR.civic, publishedAt: ago(1, '08:15'),
    examTags: ['UPSC GS-II', 'State PSC', 'SSC GK'],
    keyFacts: [
      `An ordinary bill passes through three readings in each House of Parliament.`,
      `A money bill can be introduced only in the Lok Sabha, and the Rajya Sabha must return it within 14 days.`,
      `A deadlock on an ordinary bill can be resolved by a joint sitting under Article 108, presided over by the Speaker of the Lok Sabha.`,
      `A bill becomes an Act only after the President gives assent.`
    ],
    body: [
      `A bill is a proposal for a new law or a change to an existing one. In India's Parliament, a bill moved by a minister is a government bill, while one moved by any other member is a private member's bill. Whichever kind it is, an ordinary bill must clear the same sequence in both the Lok Sabha and the Rajya Sabha before it can reach the statute book.`,
      `The first reading is simply the introduction. The member asks for leave to introduce the bill, its title and objectives are read out, and it is published in the Gazette. There is usually no debate at this stage. The real work begins at the second reading, which has two parts. First, the House discusses the principles of the bill and may send it to a select committee of that House or a joint committee of both Houses for detailed scrutiny. Then comes clause-by-clause consideration, when members can move amendments to individual provisions.`,
      `The third reading is short. Members debate only whether to accept or reject the bill as a whole, and amendments are limited to minor, formal corrections. Once passed, the bill travels to the other House, where the three readings are repeated. If the second House makes changes, the bill returns to the first House for agreement.`,
      `Disagreements can stall a bill. If one House rejects a bill passed by the other, insists on amendments the other will not accept, or does not pass it within six months, the President may summon a joint sitting of both Houses under Article 108. The Speaker of the Lok Sabha presides, and the bill is decided by a majority of members present and voting. Because the Lok Sabha is the larger House, its numbers usually carry the day. Joint sittings have been rare in practice.`,
      `Money bills follow a different path. They can be introduced only in the Lok Sabha, and only on the recommendation of the President. The Rajya Sabha can suggest changes but cannot amend or reject a money bill, and it must return the bill within 14 days, after which it is deemed passed by both Houses. There is no joint sitting for a money bill.`,
      `The final step is assent. The President may give assent, withhold it, or, for a bill that is not a money bill, return it once for reconsideration. If Parliament passes it again, with or without changes, the President must then assent. Only at that point does the bill become an Act.`
    ],
    sponsor: null,
    quiz: [
      { q: `How many readings does an ordinary bill pass through in each House?`, options: ['Two', 'Three', 'Four', 'One'], answer: 1, factRef: 0 },
      { q: `Within how many days must the Rajya Sabha return a money bill?`, options: ['7 days', '30 days', '14 days', '6 months'], answer: 2, factRef: 1 },
      { q: `Who presides over a joint sitting of the two Houses?`, options: ['The Vice-President', 'The President', 'The Chief Justice of India', 'The Speaker of the Lok Sabha'], answer: 3, factRef: 2 }
    ]
  },
  {
    id: 'polity-anti-defection',
    category: 'polity',
    title: `What the anti-defection rules are designed to stop`,
    dek: `The Tenth Schedule ties an elected member to the party they were elected on. What it covers, and where the exceptions lie.`,
    source: src('civic'), author: AUTHOR.civic, publishedAt: ago(9, '09:00'),
    examTags: ['UPSC GS-II'],
    keyFacts: [
      `The anti-defection law is contained in the Tenth Schedule, added by the 52nd Amendment Act of 1985.`,
      `A member can be disqualified for voluntarily giving up party membership or for defying the party whip in a vote.`,
      `A merger is protected only if at least two-thirds of the legislature party agree to it.`,
      `The presiding officer of the House decides disqualification cases, and the decision is open to judicial review.`
    ],
    body: [
      `Before the mid-1980s, legislators in India could switch parties with few consequences, and several state governments rose and fell as members crossed the floor, sometimes more than once in a short span. The phrase "Aaya Ram, Gaya Ram" entered political vocabulary to describe this churn. Parliament responded with the 52nd Amendment Act of 1985, which added the Tenth Schedule to the Constitution, commonly called the anti-defection law.`,
      `The law sets out when a member of Parliament or a state legislature loses their seat. A member elected on a party ticket is disqualified if they voluntarily give up membership of that party, or if they vote or abstain in the House contrary to the party's direction, known as the whip, without prior permission. The second ground does not apply if the party condones the vote within 15 days. An independent member is disqualified if they join a party after the election. A nominated member may join a party within six months of taking their seat, but not after.`,
      `The main exception concerns mergers. If a member's original party merges with another party, and at least two-thirds of the members of the legislature party agree, those members are protected from disqualification. The original schedule also protected a "split" by one-third of a legislature party, but the 91st Amendment Act of 2003 removed that exception because it was seen as easy to misuse.`,
      `Disqualification petitions are decided by the presiding officer of the House: the Speaker or the Chairman. In 1992, in the Kihoto Hollohan case, the Supreme Court upheld the law but held that the presiding officer's decision is subject to judicial review. Debate continues over how quickly such petitions should be decided, since delays can leave a member's status unresolved for long periods.`,
      `For exam purposes, it helps to remember what the law is trying to protect: the link between a voter's choice of party and the member's conduct in the House. Critics argue that strict whips also limit a legislator's freedom to vote on conscience, which is why the balance between party discipline and individual judgment remains a live topic.`
    ],
    sponsor: null,
    quiz: [
      { q: `Which schedule of the Constitution contains the anti-defection law?`, options: ['Eighth Schedule', 'Tenth Schedule', 'Seventh Schedule', 'Twelfth Schedule'], answer: 1, factRef: 0 },
      { q: `What share of a legislature party must agree for a merger to be protected?`, options: ['One-third', 'One-half', 'Two-thirds', 'Three-fourths'], answer: 2, factRef: 2 },
      { q: `Who decides a disqualification petition under the anti-defection law?`, options: ['The presiding officer of the House', 'The Election Commission', 'The President', 'The Governor'], answer: 0, factRef: 3 }
    ]
  },
  {
    id: 'polity-panchayati-raj',
    category: 'polity',
    title: `Panchayati Raj at a glance: three tiers of local government`,
    dek: `The 73rd Amendment gave village, block and district councils constitutional status. A quick guide to how the system is built.`,
    source: src('desk'), author: AUTHOR.desk, publishedAt: ago(16, '10:30'),
    examTags: ['UPSC GS-II', 'SSC GK'],
    keyFacts: [
      `The 73rd Constitutional Amendment Act of 1992 gave constitutional status to Panchayati Raj institutions.`,
      `The system has three tiers: village, intermediate (block) and district.`,
      `Panchayat elections are held every five years and are conducted by the State Election Commission.`,
      `The Eleventh Schedule lists 29 subjects that can be devolved to panchayats.`
    ],
    body: [
      `Local self-government in rural India rests on panchayats, elected councils that plan and deliver services close to where people live. The idea has deep roots, but its modern form comes from the 73rd Constitutional Amendment Act of 1992, which came into force on 24 April 1993. That date is now marked as National Panchayati Raj Day. The amendment added Part IX to the Constitution and made panchayats a constitutional requirement rather than a matter left entirely to each state.`,
      `The structure follows the three-tier model recommended by the Balwant Rai Mehta Committee in 1957. At the base is the gram panchayat for a village or group of villages. Above it sits an intermediate body, often called the panchayat samiti, at the block level, and at the top is the zila parishad for the district. States with a population below 20 lakh may skip the intermediate tier.`,
      `The foundation of the system is the gram sabha: all adults registered as voters in the village area. It is meant to review plans, approve beneficiaries of schemes and hold the elected panchayat to account. Members at every tier are directly elected, and the term is five years. If a panchayat is dissolved early, fresh elections must be held within six months. The State Election Commission conducts these elections, separately from the Election Commission of India.`,
      `The amendment also built in representation. Seats are reserved for Scheduled Castes and Scheduled Tribes in proportion to their population, and at least one-third of seats, including chairperson posts, are reserved for women. Many states have since raised the share for women to one-half.`,
      `Money and functions are the practical test. The Eleventh Schedule lists 29 subjects, from agriculture and drinking water to primary education and rural roads, that states may devolve to panchayats. A State Finance Commission, set up every five years, recommends how revenue should be shared between the state and local bodies. How much power actually flows down varies widely from state to state, which is why devolution remains a recurring theme in governance debates.`
    ],
    sponsor: null,
    quiz: [
      { q: `Which amendment gave constitutional status to Panchayati Raj institutions?`, options: ['42nd Amendment', '73rd Amendment', '74th Amendment', '86th Amendment'], answer: 1, factRef: 0 },
      { q: `Who conducts elections to panchayats?`, options: ['The State Election Commission', 'The Election Commission of India', 'The district collector', 'The State Finance Commission'], answer: 0, factRef: 2 },
      { q: `How many subjects are listed in the Eleventh Schedule?`, options: ['18', '12', '29', '22'], answer: 2, factRef: 3 }
    ]
  },
  {
    id: 'polity-election-commission',
    category: 'polity',
    title: `How an election commission runs a nationwide poll`,
    dek: `Hundreds of millions of voters, several phases and one rulebook. The machinery behind a general election in India.`,
    source: src('meridian'), author: AUTHOR.meridian, publishedAt: ago(4, '07:00'),
    examTags: ['UPSC GS-II', 'Railways GK'],
    keyFacts: [
      `The Election Commission of India is a constitutional body under Article 324 and was set up on 25 January 1950.`,
      `The Model Code of Conduct takes effect as soon as the election schedule is announced.`,
      `Electronic voting machines are paired with VVPAT units that print a paper slip the voter can see.`,
      `The NOTA option lets a voter reject all candidates on the ballot.`
    ],
    body: [
      `A general election in India is among the largest administrative exercises anywhere. The body in charge is the Election Commission of India, set up under Article 324 of the Constitution on 25 January 1950. That anniversary is observed as National Voters' Day. The Commission superintends, directs and controls elections to Parliament, state legislatures and the offices of President and Vice-President.`,
      `Work begins long before polling day. Electoral rolls are revised so that eligible citizens aged 18 and above are registered and duplicate or outdated entries are removed. Polling stations are mapped so that voters do not have to travel too far, and staff are trained for every booth. Security forces are planned in advance, which is one reason large elections are held in several phases rather than on a single day.`,
      `The moment the Commission announces the schedule, the Model Code of Conduct comes into force. It is a set of guidelines, agreed with political parties, on speeches, rallies, manifestos and the use of official machinery. Governments in office cannot announce new schemes that could influence voters, and ministers cannot combine official visits with campaigning. The code is enforced through the Commission's authority rather than a specific statute.`,
      `On polling day, votes are cast on electronic voting machines. Each machine is paired with a Voter Verifiable Paper Audit Trail unit, which prints a slip showing the chosen candidate's name and symbol. The slip is visible behind glass for a few seconds and then drops into a sealed box, giving a paper record that can be checked. Since 2013, ballots have included a NOTA option, "None of the Above", for voters who wish to reject every candidate.`,
      `Counting is overseen by returning officers, one for each constituency, with candidates' agents watching. Results are declared constituency by constituency, and the Commission then publishes the full outcome. For aspirants, the key ideas are the constitutional basis of the Commission, the role of the Model Code, and the safeguards built into voting technology.`
    ],
    sponsor: null,
    quiz: [
      { q: `Under which Article of the Constitution is the Election Commission established?`, options: ['Article 280', 'Article 315', 'Article 324', 'Article 356'], answer: 2, factRef: 0 },
      { q: `When does the Model Code of Conduct come into force?`, options: ['On polling day', 'When the election schedule is announced', 'When nominations close', 'After counting begins'], answer: 1, factRef: 1 },
      { q: `What does a VVPAT unit do?`, options: ['Counts votes for each booth', `Verifies the voter's identity`, 'Transmits results to the Commission', 'Prints a paper slip the voter can see'], answer: 3, factRef: 2 }
    ]
  },

  // ---------------------------------------------------------------- Economy
  {
    id: 'economy-repo-rate',
    category: 'economy',
    title: `What the repo rate does to your loan EMI`,
    dek: `When the central bank moves one policy rate, the effect can travel all the way to a home-loan instalment. Here is how.`,
    source: src('meridian'), author: AUTHOR.meridian, publishedAt: ago(0, '09:45'),
    examTags: ['Banking Awareness', 'UPSC GS-III'],
    keyFacts: [
      `The repo rate is the rate at which the RBI lends short-term funds to banks against government securities.`,
      `The Monetary Policy Committee that sets the repo rate has six members, three of them from the RBI.`,
      `The inflation target for monetary policy is 4% CPI inflation, with a tolerance band of 2% to 6%.`,
      `Many floating-rate retail loans are linked to an external benchmark such as the repo rate.`
    ],
    body: [
      `Banks often need short-term money to balance their books at the end of the day. One place they can borrow it is the Reserve Bank of India, which lends against government securities that the bank agrees to buy back later. The interest rate on this arrangement is the repo rate, short for repurchase. The reverse repo rate is the other side of the window: what the RBI pays when banks park surplus funds with it. Together they signal the stance of monetary policy.`,
      `The repo rate is decided by the Monetary Policy Committee, a six-member body created under the amended RBI Act. Three members come from the RBI, including the Governor, who chairs it and has a casting vote in case of a tie, and three are external members appointed by the government. The committee's job is to keep consumer price inflation at 4%, within a band of 2% to 6%. When inflation runs high, it may raise the repo rate to cool demand; when growth is weak and inflation is contained, it may cut the rate.`,
      `Why should a borrower care? Since 2019, banks have been required to link new floating-rate loans to individuals and small businesses to an external benchmark, and many chose the repo rate. A home loan might be priced at the repo rate plus a fixed spread. When the repo rate moves, the lending rate resets at the next reset date, and the EMI or the loan tenure changes with it.`,
      `Take a simple example. On a long home loan, a rise of a quarter of a percentage point in the rate can add a noticeable amount to the monthly instalment, or, if the bank keeps the EMI unchanged, stretch the loan by several months. A cut works the other way. Savers feel the effect too, because deposit rates tend to follow policy rates, though usually with a lag.`,
      `Economists call this chain transmission. It is never perfectly smooth: banks' own funding costs, competition and the share of older loans on other benchmarks all matter. For exam preparation, remember the definitions of repo and reverse repo, the composition of the Monetary Policy Committee, and the inflation target.`
    ],
    sponsor: null,
    quiz: [
      { q: `What is the repo rate?`, options: ['The rate banks pay depositors on savings', 'The rate at which the RBI lends short-term funds to banks', 'The rate the government pays on its bonds', 'The rate at which banks lend to each other overnight'], answer: 1, factRef: 0 },
      { q: `How many members sit on the Monetary Policy Committee?`, options: ['Four', 'Five', 'Six', 'Nine'], answer: 2, factRef: 1 },
      { q: `What is the CPI inflation target for monetary policy?`, options: ['6%', '5%', '2%', '4%'], answer: 3, factRef: 2 }
    ]
  },
  {
    id: 'economy-gdp-gnp-gva',
    category: 'economy',
    title: `GDP, GNP and GVA: three ways to measure an economy`,
    dek: `Three acronyms, three different questions about the same economy. A plain-language guide to what each one counts.`,
    source: src('desk'), author: AUTHOR.desk, publishedAt: ago(12, '11:00'),
    examTags: ['Banking Awareness', 'SSC GK'],
    keyFacts: [
      `GDP is the market value of all final goods and services produced within a country's borders in a given period.`,
      `GNP equals GDP plus net factor income from abroad.`,
      `GVA is output minus intermediate consumption, and GDP equals GVA plus product taxes minus product subsidies.`,
      `Real GDP adjusts for price changes, while nominal GDP is measured at current prices.`
    ],
    body: [
      `Headlines about economic growth usually quote a single number, but economists have several ways of measuring the size of an economy, and each answers a slightly different question. Three of the most common are gross domestic product, gross national product and gross value added. In India, official estimates are prepared by the National Statistics Office under the Ministry of Statistics and Programme Implementation.`,
      `Gross domestic product, or GDP, is the market value of all final goods and services produced within a country's borders during a period, usually a quarter or a year. The word final matters: flour sold to a bakery is not counted separately, because its value is already included in the price of the bread. GDP counts what is produced inside the country, regardless of whether the producer is a citizen or a foreign company.`,
      `Gross national product, or GNP, shifts the focus from place to people. It starts with GDP and adds net factor income from abroad: what residents earn from work and investments overseas, minus what foreigners earn from production inside the country. For a country with many citizens working abroad, the gap between GDP and GNP can be meaningful.`,
      `Gross value added, or GVA, looks at the economy from the supply side. For each sector, it takes the value of output and subtracts intermediate consumption, the inputs used up in production. This makes GVA useful for comparing sectors such as agriculture, manufacturing and services. The link to GDP is simple: GDP equals GVA plus taxes on products minus subsidies on products.`,
      `One more distinction trips up many students. Nominal GDP is measured at current prices, so it rises when prices rise even if nothing more is produced. Real GDP is measured at the constant prices of a chosen base year, which strips out inflation and shows the change in actual output. When growth rates are reported, they almost always refer to real GDP.`
    ],
    sponsor: null,
    quiz: [
      { q: `GNP is equal to GDP plus what?`, options: ['Indirect taxes', 'Net factor income from abroad', 'Depreciation', 'Government spending'], answer: 1, factRef: 1 },
      { q: `Which formula links GDP and GVA?`, options: ['GDP = GVA + product taxes − product subsidies', 'GDP = GVA − depreciation', 'GDP = GVA + exports − imports', 'GDP = GVA × inflation rate'], answer: 0, factRef: 2 },
      { q: `Which measure strips out the effect of price changes?`, options: ['Nominal GDP', 'GNP at current prices', 'Real GDP', 'Gross output'], answer: 2, factRef: 3 }
    ]
  },
  {
    id: 'economy-budget-process',
    category: 'economy',
    title: `How a national budget is prepared, presented and passed`,
    dek: `From ministry estimates months ahead to the Appropriation Bill in spring, the Union Budget follows a fixed constitutional path.`,
    source: src('civic'), author: AUTHOR.civic, publishedAt: ago(6, '08:00'),
    examTags: ['UPSC GS-III', 'Banking Awareness'],
    keyFacts: [
      `Article 112 of the Constitution calls the budget the Annual Financial Statement.`,
      `Since 2017, the Union Budget has been presented on 1 February, and the separate railway budget was merged into it.`,
      `Only the Lok Sabha votes on the demands for grants.`,
      `No money can be withdrawn from the Consolidated Fund of India without an Appropriation Act.`
    ],
    body: [
      `The word budget does not appear in the Constitution. Instead, Article 112 requires the President to cause an Annual Financial Statement to be laid before Parliament each year, setting out the estimated receipts and expenditure of the Government of India. The Ministry of Finance coordinates the preparation, which begins months earlier when ministries submit their estimates for the coming financial year.`,
      `The Finance Minister presents the budget in the Lok Sabha. Since 2017, the presentation has been held on 1 February, earlier than the old practice of the last working day of February, so that the budget can be passed before the financial year begins on 1 April. The same year, the separate railway budget, a practice dating from the colonial era, was merged into the Union Budget.`,
      `After the speech comes a general discussion in both Houses, followed by a recess during which departmental standing committees examine the spending plans of each ministry. The Lok Sabha then votes on the demands for grants, ministry by ministry. The Rajya Sabha can discuss the budget but does not vote on these demands. Because time is limited, the demands still undiscussed on the final day are put to vote together, a procedure known as the guillotine.`,
      `Two bills complete the process. The Appropriation Bill, under Article 114, authorises the withdrawal of money from the Consolidated Fund of India, because no money can leave that fund without a law passed by Parliament. The Finance Bill gives effect to the tax proposals. Both are money bills. If the full budget cannot be passed before 1 April, Parliament can pass a vote on account under Article 116 to cover expenses for part of the year.`,
      `A few terms are worth separating. The fiscal deficit is the gap between total expenditure and total receipts excluding borrowings, and it shows how much the government needs to borrow. Revenue expenditure keeps the government running, while capital expenditure creates assets such as roads and ports. Exam questions often combine these definitions with the constitutional articles above.`
    ],
    sponsor: null,
    quiz: [
      { q: `What does the Constitution call the budget?`, options: ['The Finance Statement', 'The Annual Financial Statement', 'The Appropriation Account', 'The Consolidated Estimate'], answer: 1, factRef: 0 },
      { q: `Which House votes on the demands for grants?`, options: ['Only the Rajya Sabha', 'Both Houses in a joint sitting', 'Only the Lok Sabha', 'Neither; the Cabinet approves them'], answer: 2, factRef: 2 },
      { q: `Since 2017, on which date has the Union Budget been presented?`, options: ['1 April', '28 February', '15 March', '1 February'], answer: 3, factRef: 1 }
    ]
  },
  {
    id: 'economy-inflation-cpi-wpi',
    category: 'economy',
    title: `Inflation explained: CPI, WPI and why the basket matters`,
    dek: `Two indices, two baskets and two different pictures of rising prices. What each one measures and which one the RBI watches.`,
    source: src('meridian'), author: AUTHOR.meridian, publishedAt: ago(2, '10:15'),
    examTags: ['Banking Awareness', 'Railways GK'],
    keyFacts: [
      `The Consumer Price Index (CPI) tracks retail prices paid by households and includes services.`,
      `The Wholesale Price Index (WPI) tracks prices at the wholesale stage and does not cover services.`,
      `The RBI's inflation target is defined in terms of headline CPI inflation.`,
      `Disinflation means prices are still rising but more slowly; deflation means the price level is falling.`
    ],
    body: [
      `Inflation is the rate at which the general level of prices rises over time. Because no one can track every price, statisticians follow a basket of goods and services, weight each item by its importance, and compare the cost of the basket from one period to the next. The choice of basket and weights shapes the answer, which is why India publishes more than one inflation measure.`,
      `The Consumer Price Index, or CPI, reflects prices that households actually pay in shops and markets. It is compiled by the National Statistics Office and covers food, fuel, clothing, housing and services such as education and health care. Food carries a large weight in the CPI basket, so a spike in vegetable or pulse prices can move the headline figure sharply.`,
      `The Wholesale Price Index, or WPI, is compiled by the Office of the Economic Adviser in the Ministry of Commerce and Industry. It tracks prices of goods at the wholesale stage, with manufactured products making up the largest share. It does not cover services, and it can react quickly to changes in global commodity prices such as crude oil and metals.`,
      `Since 2016, India's monetary policy framework has targeted CPI inflation rather than WPI. The logic is that CPI is closer to the cost of living that people experience. The target is 4%, with a tolerance band of 2% to 6%. Analysts also watch core inflation, which leaves out volatile food and fuel prices, to judge whether price pressure is broad-based.`,
      `Two related terms are often confused. Disinflation means inflation is falling but still positive: prices are rising, just more slowly. Deflation means the overall price level is actually declining, which can sound welcome but may signal weak demand. Stagflation describes high inflation combined with stagnant growth and high unemployment, a hard mix for policymakers.`
    ],
    sponsor: SPONSOR.prepsprint,
    quiz: [
      { q: `Which index includes services such as education and health care?`, options: ['CPI', 'WPI', 'Both equally', 'Neither'], answer: 0, factRef: 0 },
      { q: `Which measure is used for the RBI's inflation target?`, options: ['WPI inflation', 'Core inflation', 'Headline CPI inflation', 'The GDP deflator'], answer: 2, factRef: 2 },
      { q: `What does disinflation mean?`, options: ['The price level is falling', 'Inflation combined with low growth', 'Prices are rising faster', 'Prices are still rising, but more slowly'], answer: 3, factRef: 3 }
    ]
  },

  // ---------------------------------------------------------------- International
  {
    id: 'intl-unsc-resolution',
    category: 'international',
    title: `How a UN Security Council resolution is passed`,
    dek: `Fifteen members, nine votes and five vetoes. The arithmetic behind the UN's most powerful body.`,
    source: src('meridian'), author: AUTHOR.meridian, publishedAt: ago(3, '12:30'),
    examTags: ['UPSC GS-II'],
    keyFacts: [
      `The Security Council has 15 members: 5 permanent and 10 non-permanent members elected for two-year terms.`,
      `A substantive resolution needs at least nine votes in favour and no veto from a permanent member.`,
      `By long-standing practice, an abstention by a permanent member does not count as a veto.`,
      `Resolutions adopted under Chapter VII of the UN Charter are binding on member states.`
    ],
    body: [
      `The United Nations Security Council has primary responsibility, under the UN Charter, for maintaining international peace and security. It is the only UN body whose decisions member states are obliged to carry out, which makes the way it votes a frequent subject of exam questions and diplomatic debate.`,
      `The Council has 15 members. Five are permanent: China, France, Russia, the United Kingdom and the United States. The other ten are non-permanent members elected by the General Assembly for two-year terms, with five seats filled each year and seats distributed among regional groups. A retiring non-permanent member cannot be immediately re-elected. The presidency of the Council rotates every month among its members in English alphabetical order.`,
      `Voting rules are set out in Article 27 of the Charter. Each member has one vote. Decisions on procedural matters need nine affirmative votes. Decisions on all other matters, called substantive, need nine affirmative votes including the concurring votes of the permanent members. In practice, this means any one permanent member can block a substantive resolution by voting against it: the veto.`,
      `An important convention softens the rule. Since the Council's early years, an abstention or absence by a permanent member has not been treated as a veto. A resolution can therefore pass with a permanent member abstaining, as long as it gathers nine votes and no permanent member votes no.`,
      `What a resolution does depends on its basis. Under Chapter VI, the Council recommends ways to settle disputes peacefully. Under Chapter VII, it can determine a threat to peace and order measures such as sanctions or, as a last resort, the use of force, and these decisions are binding. Reform of the Council, including enlarging the permanent membership, has been discussed for decades, but any change requires amending the Charter.`
    ],
    sponsor: null,
    quiz: [
      { q: `How many members does the Security Council have?`, options: ['10', '15', '20', '5'], answer: 1, factRef: 0 },
      { q: `How many affirmative votes does a substantive resolution need, with no veto?`, options: ['Seven', 'Eight', 'Nine', 'Ten'], answer: 2, factRef: 1 },
      { q: `How is an abstention by a permanent member treated?`, options: ['It does not count as a veto', 'As a veto', 'As a yes vote', 'The vote is postponed'], answer: 0, factRef: 2 }
    ]
  },
  {
    id: 'intl-brics-g20-quad',
    category: 'international',
    title: `BRICS, G20 and Quad: how the groupings differ`,
    dek: `They are often mentioned in the same breath, but these three groupings differ in size, purpose and structure.`,
    source: src('desk'), author: AUTHOR.desk, publishedAt: ago(11, '09:30'),
    examTags: ['UPSC GS-II', 'Banking Awareness'],
    keyFacts: [
      `BRICS began with Brazil, Russia, India and China; South Africa joined in 2010, and the group has expanded further since 2024.`,
      `The New Development Bank, set up by BRICS, is headquartered in Shanghai.`,
      `The G20 began in 1999 as a forum of finance ministers and central bank governors, and held its first leaders' summit in 2008.`,
      `The Quad is an informal dialogue among Australia, India, Japan and the United States.`
    ],
    body: [
      `International groupings multiply acronyms, and three that appear often in current-affairs papers are BRICS, the G20 and the Quad. India belongs to all three, but they are built for different purposes and work in different ways. None of them is a treaty-based organisation like the United Nations; they are forums where governments coordinate.`,
      `BRICS started as BRIC, a label first used by economists to describe large, fast-growing emerging economies. Brazil, Russia, India and China began meeting as a group and held their first leaders' summit in 2009. South Africa joined in 2010, adding the S, and the group expanded again from 2024 with new members. Its best-known institution is the New Development Bank, headquartered in Shanghai, which lends for infrastructure and sustainable development.`,
      `The G20 is larger and more economic in focus. It brings together 19 countries, the European Union and, since 2023, the African Union. Its members account for most of the world's economic output and trade. It began in 1999 as a meeting of finance ministers and central bank governors after the Asian financial crisis, and leaders began meeting in 2008 during the global financial crisis. The presidency rotates each year, and the previous, current and next presidencies work together as a troika.`,
      `The Quad, formally the Quadrilateral Security Dialogue, is smaller and more strategic. Australia, India, Japan and the United States first cooperated after the 2004 Indian Ocean tsunami, and the dialogue was revived in 2017. It has no secretariat or charter. Its agenda covers maritime security, critical and emerging technologies, supply chains, climate and health, with a focus on a free and open Indo-Pacific.`,
      `A useful way to remember the difference: BRICS is a club of large emerging economies seeking a bigger voice in global institutions; the G20 is the main forum for coordinating economic policy among major economies; the Quad is a partnership of maritime democracies focused on the Indo-Pacific. Questions often test membership, founding dates and the institutions linked to each.`
    ],
    sponsor: null,
    quiz: [
      { q: `Which country joined BRIC in 2010 to form BRICS?`, options: ['Indonesia', 'South Africa', 'Saudi Arabia', 'Egypt'], answer: 1, factRef: 0 },
      { q: `Which of these is NOT a member of the Quad?`, options: ['Australia', 'Japan', 'Indonesia', 'United States'], answer: 2, factRef: 3 },
      { q: `The G20 began in 1999 as a meeting of whom?`, options: ['Heads of government', 'Trade ministers', 'Foreign ministers', 'Finance ministers and central bank governors'], answer: 3, factRef: 2 }
    ]
  },
  {
    id: 'intl-veridia-trade-bloc',
    category: 'international',
    title: `Fictional case study: the Republic of Veridia joins a regional trade bloc`,
    dek: `A made-up country helps explain real trade concepts: free trade areas, customs unions, rules of origin and safeguards.`,
    source: src('meridian'), author: AUTHOR.meridian, publishedAt: ago(1, '13:00'),
    examTags: ['UPSC GS-II'],
    keyFacts: [
      `In a free trade area, members remove tariffs among themselves but keep their own tariffs on outsiders.`,
      `A customs union adds a common external tariff that all members apply to non-members.`,
      `Rules of origin decide whether a product counts as made within the bloc and qualifies for lower tariffs.`,
      `A safeguard clause lets a member raise tariffs temporarily if a surge of imports seriously harms a domestic industry.`
    ],
    body: [
      `This is a fictional case study. The Republic of Veridia and its neighbours do not exist; they are used here to explain concepts that appear in real trade agreements and in exam questions on international economics.`,
      `Veridia, a mid-sized coastal economy, has applied to join the Eastern Shore Trade Community, a bloc of four neighbouring states. Its first choice is what kind of membership to seek. In a free trade area, members remove most tariffs on goods traded among themselves, but each keeps its own tariff schedule for imports from the rest of the world. In a customs union, members go further and adopt a common external tariff, so a shipment from outside faces the same duty wherever it enters the bloc. A common market adds free movement of services, capital and people.`,
      `Veridia's negotiators worry about trade deflection. In a free trade area, an outside exporter could ship goods to whichever member has the lowest tariff and then move them tariff-free across the bloc. Rules of origin prevent this. They set conditions, such as a minimum share of local value added or a change in product classification during processing, that a good must meet to count as made within the bloc. Only goods that qualify get the lower tariffs.`,
      `The talks also cover timing and protection. Veridia's small dairy sector fears competition, so the agreement phases out its dairy tariffs over ten years instead of immediately. A safeguard clause allows any member to raise tariffs temporarily if a sudden surge of imports causes serious injury to a domestic industry, subject to an investigation and time limits. There is also a sensitive list of products excluded from tariff cuts altogether.`,
      `Finally, the fictional deal sets up a dispute settlement panel and a joint committee to review the agreement every five years. For readers, the lesson is general: trade agreements are less about a single tariff cut and more about the fine print on origin, timelines, exceptions and enforcement that decides who actually benefits.`
    ],
    sponsor: null,
    quiz: [
      { q: `What does a customs union add to a free trade area?`, options: ['Free movement of workers', 'A common external tariff', 'A single currency', 'Zero tariffs with all countries'], answer: 1, factRef: 1 },
      { q: `What do rules of origin decide?`, options: ['Whether a product counts as made within the bloc', 'Which currency is used for trade', 'How tariff revenue is shared', 'Which member hosts the secretariat'], answer: 0, factRef: 2 },
      { q: `What does a safeguard clause allow?`, options: ['Permanent exclusion of a product from trade', 'Automatic entry of new members', 'A temporary tariff increase after a harmful import surge', 'Suspension of dispute panels'], answer: 2, factRef: 3 }
    ]
  },

  // ---------------------------------------------------------------- Science & Tech
  {
    id: 'scitech-rocket-orbit',
    category: 'scitech',
    title: `How a rocket reaches orbit: stages, velocity, fairing`,
    dek: `Getting to orbit is less about going up and more about going sideways very fast. The engineering in four steps.`,
    source: src('northline'), author: AUTHOR.northline, publishedAt: ago(5, '07:45'),
    examTags: ['UPSC GS-III', 'Railways GK'],
    keyFacts: [
      `A satellite in low Earth orbit travels at roughly 7.8 km per second.`,
      `Rockets use stages so that empty tanks and engines can be dropped, reducing the mass left to accelerate.`,
      `The payload fairing protects the satellite through the dense lower atmosphere and is jettisoned once the air thins.`,
      `Launching eastward from a site near the equator lets a rocket gain extra speed from Earth's rotation.`
    ],
    body: [
      `A common picture of a rocket launch is a vehicle climbing straight up. In reality, reaching orbit is mostly about horizontal speed. A satellite stays in orbit because it moves sideways so fast that, as it falls towards Earth, the surface curves away beneath it. In low Earth orbit, a few hundred kilometres up, that takes roughly 7.8 kilometres per second. Escaping Earth's gravity altogether needs about 11.2 kilometres per second.`,
      `The first challenge is mass. Most of a rocket's weight at lift-off is propellant, and carrying empty tanks all the way to orbit would waste energy. So rockets are built in stages. When a stage burns out, it is dropped, and the next stage ignites with a lighter vehicle to push. India's Polar Satellite Launch Vehicle, for example, uses four stages that alternate between solid and liquid propulsion.`,
      `The second challenge is the atmosphere. Near the ground, air is dense and a fast-moving rocket faces heavy drag and heating. The satellite rides inside a payload fairing, a streamlined nose shell that protects it. Once the rocket climbs above most of the atmosphere, the fairing splits and falls away, since carrying it further would only add weight.`,
      `The third challenge is steering. A rocket rises vertically at first to clear the densest air, then gradually tilts over in what engineers call a gravity turn, trading vertical climb for horizontal speed. Launch sites help here. Earth spins eastward, fastest at the equator, so a rocket launched towards the east from a site near the equator gets a free boost. Sriharikota, on India's east coast, lets rockets fly east over the Bay of Bengal, away from populated areas.`,
      `Finally, the upper stage places the satellite in its intended orbit. Some missions go to geostationary orbit, about 35,786 kilometres above the equator, where a satellite circles once a day and appears fixed in the sky, which is useful for communications and weather observation. Others go to sun-synchronous polar orbits, favoured for Earth imaging because they pass over places at the same local time each day.`
    ],
    sponsor: null,
    quiz: [
      { q: `Roughly how fast does a satellite in low Earth orbit travel?`, options: ['1.2 km/s', '3.5 km/s', '7.8 km/s', '11.2 km/s'], answer: 2, factRef: 0 },
      { q: `Why are rockets built in stages?`, options: ['To carry more satellites at once', 'To make launches quieter', 'To let the rocket hover before orbit', 'To drop empty tanks and engines and reduce mass'], answer: 3, factRef: 1 },
      { q: `Why do many launches head east from sites near the equator?`, options: ['To avoid the monsoon', `To gain speed from Earth's rotation`, 'To reduce air pressure', 'To reach the Moon faster'], answer: 1, factRef: 3 }
    ]
  },
  {
    id: 'scitech-quantum-computing',
    category: 'scitech',
    title: `Quantum computing in plain language: qubits and superposition`,
    dek: `Quantum computers are not simply faster laptops. What qubits are, what they might do, and why they are so hard to build.`,
    source: src('northline'), author: AUTHOR.northline, publishedAt: ago(14, '10:00'),
    examTags: ['UPSC GS-III', 'SSC GK'],
    keyFacts: [
      `A qubit can exist in a superposition of 0 and 1 until it is measured.`,
      `Entanglement links qubits so that measuring one gives information about the other.`,
      `Decoherence, the loss of quantum behaviour through interaction with the environment, is a major engineering hurdle.`,
      `A large, error-corrected quantum computer could break widely used public-key encryption, which is driving work on post-quantum cryptography.`
    ],
    body: [
      `Ordinary computers store information in bits, each of which is either 0 or 1. Everything from a text message to a video is built from long strings of these two values. Quantum computers use quantum bits, or qubits, which follow the rules of quantum mechanics and behave in ways that have no everyday equivalent.`,
      `The first property is superposition. Until it is measured, a qubit can be in a combination of 0 and 1 at the same time, described by probabilities. Measuring it forces a definite answer. The second property is entanglement: two or more qubits can be linked so that the state of one is tied to the state of another, and measuring one tells you something about its partner.`,
      `It is tempting to say a quantum computer tries every answer at once, but that is misleading. The real trick is interference. A well-designed quantum algorithm arranges the calculation so that paths leading to wrong answers cancel out and paths leading to right answers reinforce each other. That only works for certain kinds of problems, such as simulating molecules, some optimisation tasks and factoring large numbers.`,
      `Building the machines is extraordinarily hard. Qubits are fragile. Any interaction with heat, vibration or stray electromagnetic fields can destroy their quantum state, a problem called decoherence. Many designs cool their chips to within a fraction of a degree of absolute zero. Because errors creep in constantly, researchers are developing quantum error correction, which spreads one reliable logical qubit across many physical qubits.`,
      `The best-known risk concerns security. Much of today's internet encryption relies on problems, such as factoring very large numbers, that ordinary computers cannot solve in practical time. A large, error-corrected quantum computer running Shor's algorithm could break such schemes. That is why governments and companies are moving towards post-quantum cryptography, new methods designed to resist attack by both classical and quantum machines.`
    ],
    sponsor: null,
    quiz: [
      { q: `What is superposition?`, options: ['A qubit being in a combination of 0 and 1 until measured', 'A qubit copying itself onto another qubit', 'Two computers sharing one memory', 'A very fast processor clock speed'], answer: 0, factRef: 0 },
      { q: `What is decoherence?`, options: ['A method of quantum error correction', 'Linking two qubits together', 'Loss of quantum behaviour through interaction with the environment', 'A type of encryption'], answer: 2, factRef: 2 },
      { q: `Why is post-quantum cryptography being developed?`, options: ['To speed up downloads', 'Because large quantum computers could break common public-key encryption', 'To cool quantum chips', 'To replace passwords with qubits'], answer: 1, factRef: 3 }
    ]
  },
  {
    id: 'scitech-instant-payments',
    category: 'scitech',
    title: `How instant payment rails move money in seconds`,
    dek: `Scan, tap, done. Behind a two-second payment sits a layered system of banks, switches and settlement.`,
    source: src('desk'), author: AUTHOR.desk, publishedAt: ago(2, '16:00'),
    examTags: ['Banking Awareness'],
    keyFacts: [
      `UPI was launched in 2016 by the National Payments Corporation of India (NPCI).`,
      `A UPI payment is authorised with a UPI PIN, a second factor in addition to the phone.`,
      `NEFT has operated round the clock since December 2019, and RTGS since December 2020.`,
      `RTGS is meant for large-value transfers, with a minimum of ₹2 lakh.`
    ],
    body: [
      `Paying a shopkeeper by scanning a QR code takes a couple of seconds, but several institutions are involved behind the scenes. In India, the best-known instant rail is the Unified Payments Interface, or UPI, launched in 2016 by the National Payments Corporation of India, an organisation set up by banks under the guidance of the Reserve Bank of India.`,
      `UPI works as a layer on top of bank accounts. A user links an account to an app and creates a virtual payment address, which looks like name@bank and hides the account number. When the user pays, the app sends the request to the NPCI switch, which routes it to the payer's bank for debit and the payee's bank for credit. The payment is authorised with a UPI PIN, so a stolen phone alone is not enough to move money.`,
      `Money moves between customers in real time, but banks settle with each other differently. The switch records every transaction, and the net positions of banks are settled through their accounts with the Reserve Bank in batches during the day. This separation of instant customer credit and periodic interbank settlement is what lets the system handle very large volumes cheaply.`,
      `UPI sits alongside older rails. IMPS, launched in 2010, offers instant transfers at any hour and is part of the plumbing that UPI builds on. NEFT, the National Electronic Funds Transfer system, settles in half-hourly batches and has operated round the clock since December 2019. RTGS, Real Time Gross Settlement, settles each transaction individually and immediately, is meant for large values with a minimum of ₹2 lakh, and has run round the clock since December 2020.`,
      `Instant payments bring new risks. Most fraud does not break the system but tricks users, for example by sending a collect request disguised as a refund. A simple rule helps: you never need to enter your PIN to receive money. Banks and regulators keep adding safeguards such as transaction limits, device binding and alerts, while trying to keep payments fast.`
    ],
    sponsor: SPONSOR.airwave,
    quiz: [
      { q: `Which organisation launched UPI in 2016?`, options: ['The Reserve Bank of India', 'The State Bank of India', 'The National Payments Corporation of India', 'SEBI'], answer: 2, factRef: 0 },
      { q: `What is the minimum amount for an RTGS transfer?`, options: ['₹10,000', '₹50,000', '₹1 lakh', '₹2 lakh'], answer: 3, factRef: 3 },
      { q: `What authorises a UPI payment?`, options: ['The UPI PIN', 'The account number', 'The QR code alone', 'An email confirmation'], answer: 0, factRef: 1 }
    ]
  },
  {
    id: 'scitech-semiconductor-fab',
    category: 'scitech',
    title: `What a semiconductor fab does and why nations want one`,
    dek: `Chips power everything from phones to cars. A look inside the factories that make them, and why they are so hard to build.`,
    source: src('northline'), author: AUTHOR.northline, publishedAt: ago(8, '09:15'),
    examTags: ['UPSC GS-III'],
    keyFacts: [
      `Chips are built layer by layer on thin silicon wafers using photolithography.`,
      `Fabs operate cleanrooms because even tiny dust particles can ruin a chip.`,
      `A foundry manufactures chips designed by other companies, while a fabless firm designs chips but does not make them.`,
      `OSAT units handle outsourced assembly, testing and packaging of chips after fabrication.`
    ],
    body: [
      `A semiconductor fabrication plant, or fab, is where integrated circuits are made. Its raw material is a wafer, a thin disc of extremely pure silicon. On each wafer, hundreds of chips are built at once, layer upon layer, through a sequence that can run to many hundreds of steps and take weeks to complete.`,
      `The key step is photolithography. The wafer is coated with a light-sensitive material, and light is shone through a patterned mask, printing the circuit design onto the surface. Chemicals then etch away material or add new layers, and the cycle repeats. The most advanced fabs use extreme ultraviolet light, with a very short wavelength, to print features only a few nanometres across.`,
      `Because the features are so small, the environment must be exceptionally clean. Fabs are built around cleanrooms where air is filtered constantly and workers wear full-body suits, since a single particle of dust can ruin a chip. Fabs also need huge, uninterrupted supplies of electricity and ultrapure water, along with specialised gases and chemicals. Building a leading-edge fab can cost billions of dollars.`,
      `The industry is split into specialised roles. Fabless companies design chips but do not manufacture them. Foundries manufacture chips to other companies' designs. Some firms, called integrated device manufacturers, do both. After fabrication, wafers are cut into individual dies, which are assembled into packages and tested, often by outsourced semiconductor assembly and test firms, known as OSAT units.`,
      `Countries want fabs because chips have become strategic. Shortages can halt car assembly lines and electronics production, and manufacturing is concentrated in a small number of places. India launched the India Semiconductor Mission in 2021 to support fabs, OSAT units and chip design. For aspirants, the useful points are the stages of chip-making, the difference between design, fabrication and packaging, and why supply chains matter to economic security.`
    ],
    sponsor: null,
    quiz: [
      { q: `What is the process of printing circuit patterns onto a wafer called?`, options: ['Photolithography', 'Electrolysis', 'Galvanisation', 'Crystallisation'], answer: 0, factRef: 0 },
      { q: `What does a fabless company do?`, options: ['Manufactures chips for other firms', 'Only tests and packages chips', 'Designs chips but does not manufacture them', 'Supplies raw silicon wafers'], answer: 2, factRef: 2 },
      { q: `What does OSAT stand for in the chip industry?`, options: ['Optical silicon array technology', 'Outsourced semiconductor assembly and test', 'Offshore semiconductor allocation treaty', 'Open standard for advanced transistors'], answer: 1, factRef: 3 }
    ]
  },

  // ---------------------------------------------------------------- Environment & Climate
  {
    id: 'env-monsoon-science',
    category: 'environment',
    title: `What makes a monsoon: the science of seasonal winds`,
    dek: `India's rains arrive on a seasonal wind reversal driven by heat, pressure and the shape of the land.`,
    source: src('northline'), author: AUTHOR.northline, publishedAt: ago(0, '07:15'),
    examTags: ['UPSC GS-I', 'Railways GK'],
    keyFacts: [
      `The monsoon is driven largely by the difference in heating between land and sea.`,
      `The southwest monsoon usually reaches the Kerala coast around 1 June.`,
      `The Western Ghats force moist winds to rise, bringing heavy rain to the windward side and a rain shadow on the leeward side.`,
      `The northeast monsoon brings much of Tamil Nadu's rainfall between October and December.`
    ],
    body: [
      `The word monsoon comes from the Arabic mausim, meaning season, and it describes a seasonal reversal of winds. For much of India, the southwest monsoon between June and September delivers the bulk of the year's rainfall, feeding rivers, reservoirs and crops. Understanding why it happens starts with a simple idea: land and water heat up differently.`,
      `In summer, the Indian landmass heats up much faster than the surrounding oceans. Hot air over the land rises, creating an area of low pressure, while the cooler seas keep higher pressure. Winds blow from high to low pressure, so moist air from the Indian Ocean flows towards the land. The northward shift of the Inter-Tropical Convergence Zone, a belt of low pressure near the equator, reinforces this flow, and the heating of the Tibetan Plateau helps set up the upper-level winds associated with the monsoon.`,
      `The monsoon usually reaches the Kerala coast around 1 June and then advances in two branches. The Arabian Sea branch strikes the Western Ghats, where the moist air is forced to rise, cool and release heavy rain on the windward slopes. Beyond the mountains, the descending air is drier, leaving a rain shadow over parts of the Deccan plateau. The Bay of Bengal branch moves towards the northeast and then turns westward along the Himalaya across the Gangetic plains.`,
      `In autumn, the pattern reverses. As the land cools faster than the sea, winds blow from the land towards the ocean. These northeast winds pick up moisture over the Bay of Bengal and bring rain to the southeastern coast. Tamil Nadu receives much of its annual rainfall from this northeast monsoon between October and December.`,
      `The India Meteorological Department forecasts and tracks the season, describing total rainfall as a percentage of the long period average. Year-to-year variation is influenced by ocean patterns such as El Niño and the Indian Ocean Dipole. Breaks in the monsoon, when rain pauses for days or weeks, can matter as much to farmers as the seasonal total.`
    ],
    sponsor: null,
    quiz: [
      { q: `What is the main driver of the monsoon?`, options: [`The Moon's gravity`, 'Differential heating of land and sea', 'Earthquakes in the Himalaya', 'Ocean salinity'], answer: 1, factRef: 0 },
      { q: `Around which date does the southwest monsoon usually reach Kerala?`, options: ['1 March', '15 July', '1 June', '1 October'], answer: 2, factRef: 1 },
      { q: `Which state receives much of its rainfall from the northeast monsoon?`, options: ['Tamil Nadu', 'Punjab', 'Rajasthan', 'Gujarat'], answer: 0, factRef: 3 }
    ]
  },
  {
    id: 'env-carbon-markets',
    category: 'environment',
    title: `Carbon markets explained: credits, offsets and caps`,
    dek: `Putting a price on pollution is simple in theory and tricky in practice. How carbon markets are meant to work.`,
    source: src('meridian'), author: AUTHOR.meridian, publishedAt: ago(10, '08:30'),
    examTags: ['UPSC GS-III'],
    keyFacts: [
      `One carbon credit usually represents one tonne of carbon dioxide, or its equivalent, reduced or removed.`,
      `In a cap-and-trade system, a regulator sets a limit on total emissions and issues tradable allowances.`,
      `Additionality means a project would not have happened without the money from carbon credits.`,
      `Article 6 of the Paris Agreement sets rules for international cooperation, including trading of emission reductions.`
    ],
    body: [
      `Carbon markets put a price on greenhouse gas emissions so that polluters have a financial reason to cut them. The basic unit is a credit or allowance, usually equal to one tonne of carbon dioxide or the equivalent amount of another greenhouse gas. There are two broad kinds of market, and they work differently.`,
      `In a compliance market, a government sets a cap on total emissions from covered sectors, such as power plants or heavy industry, and issues allowances up to that cap. Companies must hold enough allowances to cover their emissions. Those that cut emissions cheaply can sell spare allowances to those that find cutting expensive. Over time the cap is lowered, raising the price. This is cap-and-trade. The European Union's Emissions Trading System, launched in 2005, is the best-known example.`,
      `In voluntary markets, companies and individuals buy offsets to balance out emissions they cannot yet avoid. Offsets come from projects such as protecting forests, capturing methane from landfills or supplying clean cookstoves. Their credibility depends on a few tests. Additionality asks whether the project would have happened anyway without carbon finance. Permanence asks whether the carbon will stay out of the atmosphere; a protected forest that later burns fails this test. Leakage asks whether emissions simply moved elsewhere.`,
      `Measurement and verification tie the system together. Independent auditors check that reductions really happened, and registries track each credit so that it is counted only once and retired when used. Weak verification has led to criticism of some offsets, which is why standards and ratings bodies have grown.`,
      `Internationally, Article 6 of the Paris Agreement lets countries cooperate to meet their climate targets, including by transferring emission reductions, with accounting rules to avoid double counting. In India, the Energy Conservation (Amendment) Act of 2022 enables a domestic carbon credit trading scheme. For exams, focus on the difference between cap-and-trade and offsets, and the key quality tests.`
    ],
    sponsor: null,
    quiz: [
      { q: `One carbon credit usually represents what?`, options: ['One kilogram of CO2', 'One tonne of CO2 or its equivalent', 'One tree planted', 'One hectare of forest'], answer: 1, factRef: 0 },
      { q: `What does additionality test?`, options: ['Whether the forest is large enough', 'Whether the credit is sold abroad', 'Whether emissions moved elsewhere', 'Whether the project would have happened anyway without carbon finance'], answer: 3, factRef: 2 },
      { q: `In cap-and-trade, who sets the limit on total emissions?`, options: ['Individual companies', 'The regulator or government', 'Carbon credit buyers', 'Independent auditors'], answer: 1, factRef: 1 }
    ]
  },
  {
    id: 'env-wetlands-ramsar',
    category: 'environment',
    title: `Wetlands and the Ramsar idea: why marshes matter`,
    dek: `Once dismissed as wasteland, wetlands turn out to be flood buffers, water filters and carbon stores.`,
    source: src('desk'), author: AUTHOR.desk, publishedAt: ago(19, '10:45'),
    examTags: ['UPSC GS-III', 'SSC GK'],
    keyFacts: [
      `The Ramsar Convention on wetlands was signed in 1971 in the Iranian city of Ramsar.`,
      `World Wetlands Day is observed on 2 February, the anniversary of the convention.`,
      `The Montreux Record lists Ramsar sites whose ecological character is changing or under threat.`,
      `India's first Ramsar sites, designated in 1981, were Chilika Lake and Keoladeo National Park.`
    ],
    body: [
      `Wetlands are areas where water covers the soil or saturates it for much of the year: marshes, swamps, lakes, floodplains, mangroves, peatlands and even rice paddies. For centuries they were often drained for farming or building, seen as unproductive land. Scientists now recognise them as some of the most valuable ecosystems on the planet.`,
      `Their services are practical. Wetlands soak up floodwater like sponges and release it slowly, protecting settlements downstream. They filter pollutants and sediments, recharge groundwater and support fisheries. Coastal mangroves blunt storm surges. Peatlands, though they cover a small share of land, store large amounts of carbon, which is released if they are drained or burned. Wetlands also host migratory birds that travel thousands of kilometres along set flyways.`,
      `International cooperation on wetlands began with the Convention on Wetlands, signed in 1971 in Ramsar, a city in Iran on the Caspian Sea. It is one of the oldest global environmental treaties. Countries that join designate wetlands of international importance, known as Ramsar sites, and commit to their wise use: conservation and sustainable use rather than a ban on all human activity. World Wetlands Day, on 2 February, marks the anniversary of the treaty.`,
      `Sites where the ecological character has changed, is changing or is likely to change because of human activity can be placed on the Montreux Record, a register that draws attention and support. India's first Ramsar sites, designated in 1981, were Chilika Lake in Odisha, a large brackish-water lagoon, and Keoladeo National Park in Rajasthan, famous for its birdlife. India has since added many more sites.`,
      `Designation alone does not protect a wetland. Encroachment, sewage, invasive weeds and changes in water flow from upstream dams remain common threats. Management plans that involve local communities, such as fishers and farmers who depend on the wetland, tend to work better than fences. For exam preparation, link wetland functions to broader themes such as flood management, climate change and biodiversity.`
    ],
    sponsor: null,
    quiz: [
      { q: `In which year was the Ramsar Convention signed?`, options: ['1962', '1971', '1985', '1992'], answer: 1, factRef: 0 },
      { q: `On which date is World Wetlands Day observed?`, options: ['2 February', '22 April', '5 June', '16 September'], answer: 0, factRef: 1 },
      { q: `What is the Montreux Record?`, options: [`A list of the world's largest wetlands`, 'A bird migration database', 'A treaty on mangrove trade', 'A register of Ramsar sites whose ecological character is under threat'], answer: 3, factRef: 2 }
    ]
  },
  {
    id: 'env-el-nino-la-nina',
    category: 'environment',
    title: `El Niño and La Niña: how a Pacific see-saw shifts rainfall`,
    dek: `A change in ocean temperatures thousands of kilometres away can tilt the odds for India's monsoon.`,
    source: src('northline'), author: AUTHOR.northline, publishedAt: ago(7, '11:30'),
    examTags: ['UPSC GS-I'],
    keyFacts: [
      `El Niño is the warming of sea surface temperatures in the central and eastern equatorial Pacific.`,
      `La Niña is the opposite phase, with cooler-than-normal waters and stronger trade winds.`,
      `El Niño years are often, but not always, associated with weaker Indian monsoon rainfall.`,
      `The Southern Oscillation is a see-saw in air pressure between the eastern and western tropical Pacific.`
    ],
    body: [
      `In normal years, trade winds blow from east to west across the tropical Pacific Ocean, pushing warm surface water towards Asia and Australia. Along the coast of South America, cooler water wells up from the deep. This arrangement leaves warm water and rising, rainy air over the western Pacific, and cooler, drier conditions to the east.`,
      `Every few years, the trade winds weaken and the pattern shifts. Warm water spreads eastward, and sea surface temperatures in the central and eastern equatorial Pacific rise above normal. This is El Niño, Spanish for "the little boy", a name used by fishermen in Peru who noticed warm currents appearing around Christmas. Rainfall patterns move with the warm water, bringing floods to some regions and drought to others.`,
      `La Niña is the opposite phase. The trade winds strengthen, more cold water wells up in the east, and the central and eastern Pacific become cooler than normal. The atmosphere responds too. Scientists track the Southern Oscillation, a see-saw in surface air pressure between the eastern and western tropical Pacific, often measured with pressure readings at Tahiti and Darwin. Together, the ocean and atmosphere changes are called ENSO, the El Niño–Southern Oscillation.`,
      `For India, ENSO matters because it shifts the large-scale circulation that feeds the monsoon. El Niño years are often associated with below-normal monsoon rainfall and a higher risk of drought, while La Niña years tend to favour normal or above-normal rain. But the link is not fixed: some El Niño years have seen normal rainfall. Other factors, such as the Indian Ocean Dipole, a temperature difference between the western and eastern Indian Ocean, can offset or reinforce ENSO's influence.`,
      `Because ENSO develops over months, forecasters use it to issue early outlooks for the monsoon, crop planning and water management. For exams, remember the basic definitions, the direction of the trade winds in each phase, and the fact that ENSO shifts the odds rather than deciding the outcome.`
    ],
    sponsor: null,
    quiz: [
      { q: `What happens to sea surface temperatures in the eastern Pacific during El Niño?`, options: ['They become cooler', 'They become warmer than normal', 'They freeze over', 'They do not change'], answer: 1, factRef: 0 },
      { q: `How are the trade winds during La Niña?`, options: ['Weaker than normal', 'Reversed completely', 'Stronger than normal', 'Absent'], answer: 2, factRef: 1 },
      { q: `Between which regions does the Southern Oscillation see-saw in air pressure?`, options: ['Eastern and western tropical Pacific', 'North and south Atlantic', 'The Arctic and Antarctic', 'Arabian Sea and Bay of Bengal'], answer: 0, factRef: 3 }
    ]
  },

  // ---------------------------------------------------------------- Health
  {
    id: 'health-vaccines',
    category: 'health',
    title: `How vaccines train the immune system`,
    dek: `A vaccine gives the body a safe preview of a germ so that it can respond fast when the real thing arrives.`,
    source: src('northline'), author: AUTHOR.northline, publishedAt: ago(13, '09:00'),
    examTags: ['SSC GK', 'Railways GK'],
    keyFacts: [
      `Vaccines expose the immune system to an antigen so it can build memory without causing the disease.`,
      `Memory B and T cells allow a faster and stronger response on later exposure.`,
      `Herd immunity protects people who cannot be vaccinated when enough of the population is immune.`,
      `Many vaccines must be kept within a set temperature range from factory to clinic, a system called the cold chain.`
    ],
    body: [
      `The immune system learns from experience. When a germ such as a virus or bacterium enters the body, immune cells recognise molecules on its surface called antigens. It takes days to mount a full response the first time, which is why people fall ill. A vaccine offers a safer way to learn: it shows the immune system an antigen without causing the disease.`,
      `Two kinds of white blood cells do much of the work. B cells produce antibodies, proteins that latch onto a specific antigen and help neutralise the germ. T cells help coordinate the response and destroy infected cells. After the threat passes, some of these cells remain as memory cells. If the same germ appears again, memory cells recognise it quickly and trigger a faster and stronger response, often stopping illness before it starts.`,
      `Vaccines deliver antigens in different ways. Live attenuated vaccines use a weakened form of the germ. Inactivated vaccines use a killed germ. Subunit vaccines use only a piece of it, such as a protein. Newer approaches, including mRNA and viral vector vaccines, give the body instructions to make a harmless antigen itself. Some vaccines need several doses or later boosters to build and maintain protection.`,
      `Vaccination protects communities as well as individuals. When a large enough share of people are immune, a germ struggles to spread, which shields those who cannot be vaccinated, such as newborns or people with certain medical conditions. This is herd immunity. The threshold depends on how contagious the disease is.`,
      `Delivering vaccines is a logistics challenge. Many must be kept within a narrow temperature range from factory to clinic, through a network of refrigerators and insulated carriers called the cold chain. India's Universal Immunisation Programme, launched in 1985, provides routine vaccines to infants and pregnant women. The history of vaccination goes back to 1796, when Edward Jenner used material from cowpox to protect against smallpox, a disease later eradicated worldwide.`
    ],
    sponsor: null,
    quiz: [
      { q: `What do vaccines expose the immune system to?`, options: ['An antibiotic', 'An antigen', 'A hormone', 'A vitamin'], answer: 1, factRef: 0 },
      { q: `Which cells allow a faster response on later exposure?`, options: ['Red blood cells', 'Platelets', 'Memory B and T cells', 'Nerve cells'], answer: 2, factRef: 1 },
      { q: `What is the cold chain?`, options: ['A common vaccine side effect', 'A type of booster dose', 'A laboratory test for antibodies', 'The system that keeps vaccines within a set temperature range'], answer: 3, factRef: 3 }
    ]
  },
  {
    id: 'health-amr',
    category: 'health',
    title: `Antimicrobial resistance: why antibiotics stop working`,
    dek: `Bacteria evolve. Every unnecessary course of antibiotics gives the resistant ones a head start.`,
    source: src('northline'), author: AUTHOR.northline, publishedAt: ago(4, '14:00'),
    examTags: ['UPSC GS-II'],
    keyFacts: [
      `Antimicrobial resistance happens when microbes evolve so that medicines no longer kill them or stop their growth.`,
      `Antibiotics do not work against viral infections such as the common cold.`,
      `The WHO AWaRe framework groups antibiotics into Access, Watch and Reserve categories.`,
      `Penicillin, the first widely used antibiotic, was discovered by Alexander Fleming in 1928.`
    ],
    body: [
      `Antibiotics transformed medicine in the twentieth century. The story often begins in 1928, when Alexander Fleming noticed that a mould called Penicillium had killed bacteria growing in a dish in his laboratory. Penicillin, developed into a medicine in the following years, made once-deadly infections treatable and made modern surgery, cancer treatment and organ transplants far safer.`,
      `Antimicrobial resistance, or AMR, is the ability of bacteria, viruses, fungi and parasites to survive medicines designed to kill them. It is driven by evolution. In any large population of bacteria, a few may carry genetic changes that help them survive a drug. When an antibiotic kills the susceptible ones, the resistant survivors multiply, and they can even pass resistance genes to other bacteria.`,
      `Human behaviour speeds this up. Antibiotics are often taken for viral infections such as colds and flu, where they have no effect on the virus but still expose the body's bacteria to the drug. Stopping a course early, using leftover medicines, buying antibiotics without a prescription and heavy use in livestock and poultry all add pressure. Poor sanitation and infection control in hospitals help resistant strains spread.`,
      `To guide use, the World Health Organization groups antibiotics under its AWaRe framework. Access antibiotics are first choices for common infections. Watch antibiotics carry a higher risk of resistance and should be used more carefully. Reserve antibiotics are last-resort options for multidrug-resistant infections. In India, a red vertical line on the packaging of some medicines signals that they should be sold only on prescription.`,
      `The stakes are high, because infections that no longer respond to treatment make routine care riskier. Responses include surveillance of resistance patterns, better diagnostics so that doctors know which drug to use, vaccination to prevent infections in the first place, and research into new antibiotics. The One Health approach treats human, animal and environmental health as linked, since resistance moves between all three.`
    ],
    sponsor: null,
    quiz: [
      { q: `Why do antibiotics not help with the common cold?`, options: ['The cold is caused by a virus', 'The cold is caused by a fungus', 'Antibiotics only work in children', 'The cold is too mild'], answer: 0, factRef: 1 },
      { q: `What do the letters in the WHO AWaRe framework stand for?`, options: ['Antibiotic, Warning, Restricted', 'Access, Watch, Reserve', 'Active, Weak, Resistant', 'Approved, Waiting, Rejected'], answer: 1, factRef: 2 },
      { q: `Who discovered penicillin in 1928?`, options: ['Louis Pasteur', 'Edward Jenner', 'Alexander Fleming', 'Robert Koch'], answer: 2, factRef: 3 }
    ]
  },
  {
    id: 'health-surveillance',
    category: 'health',
    title: `What a public-health surveillance system watches for`,
    dek: `Before an outbreak makes headlines, it usually shows up as a small change in numbers. Surveillance is how health systems spot it.`,
    source: src('civic'), author: AUTHOR.civic, publishedAt: ago(21, '08:45'),
    examTags: ['UPSC GS-II'],
    keyFacts: [
      `Public-health surveillance is the continuous, systematic collection and analysis of health data to guide action.`,
      `India's Integrated Disease Surveillance Programme (IDSP) was launched in 2004.`,
      `IDSP uses syndromic, presumptive and laboratory-confirmed reporting formats.`,
      `Wastewater surveillance tests sewage to detect pathogens circulating in a community.`
    ],
    body: [
      `Public-health surveillance is the continuous, systematic collection, analysis and interpretation of health data, used to plan and guide action. It works like an early-warning radar: rather than waiting for a crisis, it looks for small changes, such as an unusual cluster of fever cases in one district, that could signal an outbreak.`,
      `India's main platform is the Integrated Disease Surveillance Programme, launched in 2004. It gathers weekly data from health facilities in three formats. Syndromic reporting, often from frontline health workers, records patterns of symptoms, such as fever with rash. Presumptive reporting comes from doctors who suspect a particular disease based on clinical signs. Laboratory reporting confirms the diagnosis with tests. Facilities are asked to report even when they have no cases, called zero reporting, so that silence is not mistaken for safety.`,
      `When numbers cross a set threshold, rapid response teams investigate, collect samples and advise on control measures, from clean water supply to vector control for mosquito-borne diseases. The data flows through district and state units to the national level, and digital platforms have made reporting faster than the older paper-based systems.`,
      `Surveillance is widening in scope. Wastewater surveillance tests sewage for traces of pathogens, giving a picture of what is circulating in a community, including infections that people do not report. The One Health approach links human health data with animal and environmental monitoring, since many new infections, such as some influenza strains, come from animals.`,
      `At the global level, the International Health Regulations of 2005 require countries to build surveillance capacity and to notify the World Health Organization of events that may be public-health emergencies of international concern. For aspirants, the key ideas are what surveillance is, how IDSP is organised, and why new tools like wastewater testing extend what traditional reporting can see.`
    ],
    sponsor: null,
    quiz: [
      { q: `When was India's Integrated Disease Surveillance Programme launched?`, options: ['1985', '1997', '2004', '2015'], answer: 2, factRef: 1 },
      { q: `Which of these is one of IDSP's reporting formats?`, options: ['Syndromic', 'Genetic', 'Financial', 'Seasonal'], answer: 0, factRef: 2 },
      { q: `What does wastewater surveillance test?`, options: ['Drinking water quality only', 'Air inside hospitals', 'Fish populations in rivers', 'Sewage, for traces of pathogens circulating in a community'], answer: 3, factRef: 3 }
    ]
  },

  // ---------------------------------------------------------------- Sports & Awards
  {
    id: 'sports-olympic-host',
    category: 'sports',
    title: `How the Olympic Games are awarded to a host city`,
    dek: `Hosting the Olympics takes years of planning before a single ticket is sold. How the choice is made.`,
    source: src('meridian'), author: AUTHOR.meridian, publishedAt: ago(17, '12:00'),
    examTags: ['SSC GK', 'Railways GK'],
    keyFacts: [
      `The International Olympic Committee (IOC), headquartered in Lausanne, Switzerland, awards the Games.`,
      `Since reforms adopted in 2019, Future Host Commissions hold continuous dialogue with interested hosts instead of running a fixed bidding race.`,
      `The final decision is made by a vote of IOC members at an IOC Session.`,
      `The first modern Olympic Games were held in Athens in 1896.`
    ],
    body: [
      `The Olympic Games are owned by the International Olympic Committee, a non-governmental body founded in 1894 and headquartered in Lausanne, Switzerland. The first modern Games were held in Athens in 1896. Since then, the choice of host has become one of the most closely watched decisions in sport, with consequences for city budgets, infrastructure and national prestige.`,
      `For decades, cities competed in long, expensive bidding races that ended in a single vote. Rising costs and a shrinking field of willing candidates pushed the IOC to change the process. Under reforms adopted in 2019, Future Host Commissions for the Summer and Winter Games hold continuous, non-committal dialogue with interested cities, regions or countries. They study proposals, encourage the use of existing and temporary venues, and can move to targeted dialogue with a preferred candidate.`,
      `The IOC's Executive Board then proposes a host, and the final decision is made by a vote of IOC members at an IOC Session, the organisation's general assembly. The winning host signs a host contract setting out obligations on venues, transport, security, athlete accommodation and broadcasting. Hosts are typically chosen around seven years before the Games, and sometimes earlier.`,
      `Candidates are assessed on several factors: public and government support, the financial plan and guarantees, venue plans, accommodation, transport, security, sustainability and legacy, meaning how facilities and programmes will benefit the host after the Games. Many recent plans rely heavily on existing stadiums to limit the risk of expensive venues standing empty afterwards.`,
      `For readers, the key facts are simple: the IOC awards the Games, IOC members vote at a Session, and the process now favours dialogue over bidding wars. The Olympic symbol of five interlocking rings represents the union of the continents and the meeting of athletes from around the world. National Olympic Committees, such as the Indian Olympic Association, represent their countries within the movement.`
    ],
    sponsor: null,
    quiz: [
      { q: `Where is the International Olympic Committee headquartered?`, options: ['Geneva', 'Lausanne', 'Paris', 'Athens'], answer: 1, factRef: 0 },
      { q: `Who casts the final vote to award the Games?`, options: [`The host country's parliament`, 'The United Nations', 'IOC members at an IOC Session', 'World sports ministers'], answer: 2, factRef: 2 },
      { q: `Where were the first modern Olympic Games held in 1896?`, options: ['Athens', 'London', 'Paris', 'Rome'], answer: 0, factRef: 3 }
    ]
  },
  {
    id: 'sports-national-awards',
    category: 'sports',
    title: `India's national sports awards and what each recognises`,
    dek: `From the Khel Ratna to the Dronacharya, each national sports award has a distinct purpose.`,
    source: src('desk'), author: AUTHOR.desk, publishedAt: ago(24, '10:00'),
    examTags: ['SSC GK', 'Railways GK'],
    keyFacts: [
      `The Major Dhyan Chand Khel Ratna is India's highest sporting honour for athletes.`,
      `The Arjuna Award, instituted in 1961, recognises consistent outstanding performance.`,
      `The Dronacharya Award honours coaches whose trainees excel at major international events.`,
      `National Sports Day is observed on 29 August, the birth anniversary of hockey player Major Dhyan Chand.`
    ],
    body: [
      `India's national sports awards are given each year by the Ministry of Youth Affairs and Sports to recognise athletes, coaches, institutions and organisations. They are traditionally presented by the President at Rashtrapati Bhavan. Knowing which award goes to whom is a staple of general-knowledge sections in competitive exams.`,
      `The highest honour for sportspersons is the Major Dhyan Chand Khel Ratna, first given in 1991–92 and renamed in 2021 after the hockey player often called the wizard of the game. It recognises the most outstanding performance by an athlete over a period of years. The Arjuna Award, instituted in 1961, recognises consistent outstanding performance over the previous years, along with qualities such as leadership and sportsmanship.`,
      `Coaches have their own award. The Dronacharya Award, instituted in 1985 and named after the teacher in the Mahabharata, honours coaches who have trained sportspersons to excel at major international events. There is also recognition for lifetime contribution to sport, for athletes and coaches who continue to promote their games after retiring from competition.`,
      `Institutions are recognised too. The Maulana Abul Kalam Azad Trophy, often called the MAKA Trophy, goes to the university with the best overall performance in inter-university sport. The Rashtriya Khel Protsahan Puruskar recognises organisations and corporate bodies that have helped promote sport, for example through training, infrastructure or support for athletes.`,
      `The awards are usually announced around National Sports Day, observed on 29 August, the birth anniversary of Major Dhyan Chand, who helped India win Olympic hockey gold in 1928, 1932 and 1936. Selection committees review nominations against published criteria, which can change over time, so it is worth checking the latest guidelines before an exam. Each award carries a citation and prize money, and the Arjuna Award is accompanied by a bronze statuette of Arjuna, the archer of the Mahabharata.`
    ],
    sponsor: null,
    quiz: [
      { q: `Which award honours coaches?`, options: ['Arjuna Award', 'Dronacharya Award', 'Khel Ratna', 'MAKA Trophy'], answer: 1, factRef: 2 },
      { q: `When is National Sports Day observed?`, options: ['15 August', '5 September', '29 August', '14 November'], answer: 2, factRef: 3 },
      { q: `In which year was the Arjuna Award instituted?`, options: ['1961', '1947', '1985', '2002'], answer: 0, factRef: 1 }
    ]
  },
  {
    id: 'sports-veridia-games',
    category: 'sports',
    title: `Fictional: how the Veridia Games organised a new multi-sport event`,
    dek: `A made-up event in a made-up country shows what organisers juggle: venues, volunteers, anti-doping and legacy.`,
    source: src('meridian'), author: AUTHOR.meridian, publishedAt: ago(6, '15:30'),
    examTags: ['General'],
    keyFacts: [
      `The Veridia Games in this story are fictional and are used only to explain how multi-sport events are organised.`,
      `The fictional organisers reused existing venues and built temporary ones to keep costs down.`,
      `Anti-doping rules for major events typically follow the World Anti-Doping Code.`,
      `Legacy planning decides how venues and programmes will be used after the event ends.`
    ],
    body: [
      `This is a fictional explainer. The Republic of Veridia and its Veridia Games are invented, used here to show the decisions behind any large multi-sport event. The details are illustrative and do not describe a real competition.`,
      `In the story, Veridia decides to host a new regional event for twelve sports, eight years after the idea is first proposed. Its organising committee begins with a venue audit. Rather than building a stadium for each sport, it reuses a university athletics ground, converts an exhibition centre into halls for badminton and table tennis, and builds temporary stands for beach volleyball that will be taken down afterwards. The committee's rule of thumb: build permanently only where there is a clear use after the Games.`,
      `Next come people. The fictional Games recruit several thousand volunteers, from drivers to scorers, and train them months ahead. An athletes' village is planned as future student housing, so it can be sold or leased after the event. Transport planners add dedicated bus lanes during competition and schedule events so that crowds do not overwhelm any single station. Medical teams, accreditation centres and a main media centre are planned alongside, since thousands of officials and journalists need daily access to venues.`,
      `Integrity is built in from the start. The organisers adopt the World Anti-Doping Code, which sets common rules on banned substances, testing and sanctions across sports and countries, and they appoint independent testing staff. Technical officials from each sport's international federation certify venues and results. Timing, scoring and photo-finish systems are tested at smaller trial events a year ahead, a common practice before large games.`,
      `Finally, the committee publishes a legacy plan: which venues stay, which go, what community sports programmes will run afterwards, and how the budget will be audited. For readers, the fictional example highlights questions to ask about any real event: are the venues needed later, who pays, and how is fair play protected?`
    ],
    sponsor: null,
    quiz: [
      { q: `Which rulebook do major events typically follow for anti-doping?`, options: ['The Olympic Charter', 'The Geneva Convention', 'The World Anti-Doping Code', 'The FIFA Statutes'], answer: 2, factRef: 2 },
      { q: `What is legacy planning about?`, options: ['Choosing music for the opening ceremony', 'Selling broadcast rights abroad', 'Designing the medals', 'How venues and programmes will be used after the event'], answer: 3, factRef: 3 },
      { q: `How did the fictional organisers keep venue costs down?`, options: ['By cancelling half the sports', 'By reusing existing venues and building temporary ones', 'By holding events only at night', 'By using only outdoor venues'], answer: 1, factRef: 1 }
    ]
  },

  // ---------------------------------------------------------------- History & Culture
  {
    id: 'history-harappan-cities',
    category: 'history',
    title: `The Harappan cities: planning, drainage and trade`,
    dek: `More than four thousand years ago, cities in the Indus region had grid streets, covered drains and standard weights.`,
    source: src('desk'), author: AUTHOR.desk, publishedAt: ago(3, '09:30'),
    examTags: ['UPSC GS-I', 'SSC GK'],
    keyFacts: [
      `The mature phase of the Harappan (Indus Valley) Civilisation is dated to roughly 2600–1900 BCE.`,
      `Harappan cities had planned streets and covered drainage systems.`,
      `Bricks were made in a standard ratio of 1:2:4 for thickness, width and length.`,
      `Lothal in Gujarat is known for a structure widely interpreted as a dockyard.`,
      `The Harappan script has not yet been deciphered.`
    ],
    body: [
      `The Harappan or Indus Valley Civilisation was one of the world's earliest urban cultures, flourishing along the Indus and neighbouring river systems in what are now Pakistan and northwestern India. Its mature, urban phase is usually dated to about 2600 to 1900 BCE. Excavations at Harappa began in 1921 and at Mohenjo-daro in 1922, revealing cities far older than historians had expected.`,
      `The most striking feature is planning. Many Harappan settlements were laid out with streets running roughly at right angles, dividing the city into blocks. Houses were built of baked or sun-dried bricks made to a standard ratio of 1:2:4 for thickness, width and length, a uniformity found across sites hundreds of kilometres apart. Many cities had a raised citadel and a larger lower town.`,
      `Sanitation was remarkably advanced. Houses often had bathing areas connected to street drains, which were covered with bricks or stone slabs and fitted with inspection points. At Mohenjo-daro, the Great Bath, a large watertight pool lined with fired bricks and sealed with bitumen, is thought to have been used for ritual bathing.`,
      `Trade linked the cities to a wide world. Standardised weights, usually cubes of chert, suggest regulated exchange. Seals carved with animals and short inscriptions were probably used to mark goods. Harappan seals and beads have been found in Mesopotamia, and Lothal in Gujarat has a large basin widely interpreted as a dockyard. Dholavira, in the Rann of Kutch, is known for its sophisticated water reservoirs and was inscribed as a UNESCO World Heritage Site in 2021.`,
      `Much remains unknown. The Harappan script, found on seals and tablets, has not been deciphered, so there are no readable records of rulers or beliefs. Researchers link the decline of the cities after about 1900 BCE to a mix of factors, including shifts in rivers and climate and changes in trade, rather than a single catastrophe.`
    ],
    sponsor: SPONSOR.pageturn,
    quiz: [
      { q: `What was the standard ratio of Harappan bricks (thickness : width : length)?`, options: ['1:1:2', '1:2:3', '1:2:4', '1:3:6'], answer: 2, factRef: 2 },
      { q: `Which Harappan site is known for a structure interpreted as a dockyard?`, options: ['Lothal', 'Kalibangan', 'Harappa', 'Rakhigarhi'], answer: 0, factRef: 3 },
      { q: `What is true of the Harappan script?`, options: ['It was an early form of Sanskrit', 'It was written only on palm leaves', 'It used an alphabet of 26 letters', 'It has not yet been deciphered'], answer: 3, factRef: 4 }
    ]
  },
  {
    id: 'history-constituent-assembly',
    category: 'history',
    title: `How a constituent assembly drafts a constitution`,
    dek: `India's Constitution took nearly three years to write. How the assembly was formed, organised and reached agreement.`,
    source: src('civic'), author: AUTHOR.civic, publishedAt: ago(22, '11:15'),
    examTags: ['UPSC GS-I', 'SSC GK'],
    keyFacts: [
      `India's Constituent Assembly first met on 9 December 1946.`,
      `The Drafting Committee was chaired by Dr B. R. Ambedkar.`,
      `The Constitution was adopted on 26 November 1949, now observed as Constitution Day.`,
      `The Constitution came into force on 26 January 1950.`
    ],
    body: [
      `A constituent assembly is a body convened to write a constitution. India's Constituent Assembly was formed under the Cabinet Mission Plan of 1946, with most members elected indirectly by the members of the provincial legislative assemblies rather than by direct popular vote. Representatives of the princely states were also included. After Partition, the assembly continued as the constitution-making body for India.`,
      `The assembly first met on 9 December 1946, with Dr Sachchidananda Sinha as its temporary chairman. Dr Rajendra Prasad was soon elected its permanent President. On 13 December 1946, Jawaharlal Nehru moved the Objectives Resolution, setting out the ideals the constitution would pursue: a sovereign republic, justice, liberty and equality for all citizens, and safeguards for minorities. Its spirit later shaped the Preamble.`,
      `Much of the detailed work happened in committees covering subjects such as fundamental rights, minorities, the powers of the Union and the states. On 29 August 1947, the assembly set up a Drafting Committee under the chairmanship of Dr B. R. Ambedkar to turn the decisions into a coherent text. The draft was published so that the public could comment, and it was then debated in the assembly clause by clause, with thousands of amendments proposed.`,
      `The framers drew on many sources, including the Government of India Act of 1935 and the constitutions of other countries, adapting ideas such as parliamentary government, fundamental rights and directive principles to Indian conditions. The whole process took about two years, eleven months and eighteen days.`,
      `The Constitution was adopted on 26 November 1949, a date observed since 2015 as Constitution Day, or Samvidhan Divas. Some provisions, such as those on citizenship and elections, took effect immediately. The rest came into force on 26 January 1950, a date chosen for its link to the 1930 declaration of Purna Swaraj and celebrated since as Republic Day.`
    ],
    sponsor: null,
    quiz: [
      { q: `Who chaired the Drafting Committee of the Constituent Assembly?`, options: ['Dr Rajendra Prasad', 'Dr B. R. Ambedkar', 'Jawaharlal Nehru', 'Dr Sachchidananda Sinha'], answer: 1, factRef: 1 },
      { q: `On which date was the Constitution adopted?`, options: ['26 January 1950', '15 August 1947', '26 November 1949', '9 December 1946'], answer: 2, factRef: 2 },
      { q: `When did the Constituent Assembly first meet?`, options: ['9 December 1946', '26 January 1950', '29 August 1947', '13 December 1946'], answer: 0, factRef: 0 }
    ]
  },
  {
    id: 'history-classical-dance',
    category: 'history',
    title: `Classical dance forms of India: a quick guide`,
    dek: `Eight dance traditions, eight regions and one ancient treatise. A starter map for general-knowledge questions.`,
    source: src('desk'), author: AUTHOR.desk, publishedAt: ago(27, '10:30'),
    examTags: ['SSC GK', 'Railways GK'],
    keyFacts: [
      `The Sangeet Natak Akademi recognises eight classical dance forms.`,
      `Bharatanatyam is associated with Tamil Nadu, and Kathakali with Kerala.`,
      `Sattriya, from Assam, was recognised as a classical dance in 2000.`,
      `The Natya Shastra, attributed to Bharata Muni, is the classic ancient text on the performing arts.`
    ],
    body: [
      `India's classical dance traditions combine rhythm, gesture, music and storytelling. Most trace their principles to the Natya Shastra, an ancient treatise on drama, dance and music attributed to the sage Bharata Muni. It describes the expression of emotion through rasa and bhava, and a vocabulary of hand gestures known as mudras. Classical forms usually combine nritta, pure dance, with nritya, expressive dance, and natya, the dramatic element.`,
      `The Sangeet Natak Akademi, India's national academy for music, dance and drama, recognises eight classical forms. Bharatanatyam, from Tamil Nadu, is known for its geometric poses, precise footwork and expressive storytelling. Kathak, which developed in north India, features fast spins and intricate footwork, with ankle bells marking complex rhythms. Kathakali, from Kerala, is a dance-drama with elaborate costumes and painted faces, often telling stories from the epics.`,
      `Kuchipudi, named after a village in Andhra Pradesh, blends dance and drama and sometimes includes dancing on the rim of a brass plate. Odissi, from Odisha, is recognised by its sculpture-like poses and the tribhangi, a posture that bends the body in three places. Manipuri, from Manipur, is gentle and flowing, with themes often drawn from the Radha–Krishna tradition.`,
      `Mohiniyattam, also from Kerala, is a graceful solo dance with swaying movements, traditionally performed by women. Sattriya, from Assam, developed in the monasteries known as sattras, founded in the tradition of the saint-reformer Srimanta Sankardeva, and was recognised as a classical form in 2000, the most recent addition to the list.`,
      `Some lists include more than eight forms; the Ministry of Culture, for instance, also includes Chhau. For exam questions, pairing each dance with its state is the most common test. A helpful trick is to remember that Kerala has two entries on the list, Kathakali and Mohiniyattam. Questions may also test distinctive features, such as the painted faces of Kathakali or the brass plate of Kuchipudi.`
    ],
    sponsor: null,
    quiz: [
      { q: `How many classical dance forms does the Sangeet Natak Akademi recognise?`, options: ['Six', 'Seven', 'Eight', 'Ten'], answer: 2, factRef: 0 },
      { q: `Which classical dance form is associated with Kerala?`, options: ['Odissi', 'Kathakali', 'Kuchipudi', 'Sattriya'], answer: 1, factRef: 1 },
      { q: `Which ancient text is the classic treatise on the performing arts?`, options: ['Natya Shastra', 'Arthashastra', 'Rigveda', 'Panchatantra'], answer: 0, factRef: 3 }
    ]
  }
];

function enrich(article) {
  const wordCount = article.body.join(' ').split(/\s+/).filter(Boolean).length;
  const readingTime = Math.max(1, Math.ceil(wordCount / ECONOMY.read.wpm));
  return Object.freeze({ ...article, wordCount, readingTime });
}

export const ARTICLES = Object.freeze(RAW.map(enrich));
export const ARTICLE_MAP = Object.freeze(Object.fromEntries(ARTICLES.map((a) => [a.id, a])));

export function getArticle(id) {
  return ARTICLE_MAP[id] || null;
}
