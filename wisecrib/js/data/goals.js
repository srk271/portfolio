// 5 reader goals. Weights (0-3) and exam-tag matches drive feed ranking.
export const GOALS = Object.freeze([
  {
    key: 'upsc',
    label: 'UPSC / State PSC',
    description: 'Civil services prelims and mains: polity, economy, world affairs and more.',
    icon: 'pillar',
    tagMatches: ['UPSC', 'State PSC'],
    weights: { polity: 3, economy: 3, international: 3, environment: 3, scitech: 2, history: 2, health: 1, sports: 1 },
    defaultInterests: ['polity', 'economy', 'international']
  },
  {
    key: 'ssc',
    label: 'SSC',
    description: 'Staff Selection exams: static GK, history, science and current affairs.',
    icon: 'book',
    tagMatches: ['SSC'],
    weights: { history: 3, polity: 2, scitech: 2, economy: 2, sports: 2, health: 1, international: 1, environment: 1 },
    defaultInterests: ['history', 'scitech', 'sports']
  },
  {
    key: 'banking',
    label: 'Banking',
    description: 'Bank PO and clerk exams: banking awareness, economy and payments.',
    icon: 'chart',
    tagMatches: ['Banking Awareness'],
    weights: { economy: 3, polity: 1, international: 2, scitech: 2, environment: 0, health: 0, sports: 0, history: 0 },
    defaultInterests: ['economy', 'international', 'scitech']
  },
  {
    key: 'railways',
    label: 'Railways',
    description: 'RRB exams: general science, GK and current events.',
    icon: 'train',
    tagMatches: ['Railways'],
    weights: { scitech: 2, history: 2, sports: 2, polity: 1, economy: 1, environment: 1, health: 1, international: 0 },
    defaultInterests: ['scitech', 'sports', 'history']
  },
  {
    key: 'general',
    label: 'General reader',
    description: 'No exam, just curious. A balanced mix of explainers.',
    icon: 'sparkle',
    tagMatches: ['General'],
    weights: { polity: 1, economy: 1, international: 1, scitech: 1, environment: 1, health: 1, sports: 1, history: 1 },
    defaultInterests: ['scitech', 'environment', 'international']
  }
].map(Object.freeze));

export const GOAL_MAP = Object.freeze(Object.fromEntries(GOALS.map((g) => [g.key, g])));

export function getGoal(key) {
  return GOAL_MAP[key] || GOAL_MAP.general;
}
