// 8 content categories. Gradients drive the deterministic cover artwork.
export const CATEGORIES = Object.freeze([
  { key: 'polity', label: 'Polity & Governance', short: 'Polity', gradient: ['#3F3DD8', '#7C4DFF'], color: '#4F46E5', glyph: 'pillar', examAreas: ['UPSC GS-II', 'State PSC'] },
  { key: 'economy', label: 'Economy & Banking', short: 'Economy', gradient: ['#0B7A5E', '#2BC48A'], color: '#0E9F6E', glyph: 'chart', examAreas: ['UPSC GS-III', 'Banking Awareness'] },
  { key: 'international', label: 'International Relations', short: 'World', gradient: ['#1257B0', '#42A5F5'], color: '#2479D8', glyph: 'globe', examAreas: ['UPSC GS-II'] },
  { key: 'scitech', label: 'Science & Tech', short: 'Sci-Tech', gradient: ['#6A1B9A', '#D05CE3'], color: '#9C3BC6', glyph: 'atom', examAreas: ['UPSC GS-III', 'SSC GK'] },
  { key: 'environment', label: 'Environment & Climate', short: 'Environment', gradient: ['#2E7D32', '#9CCC65'], color: '#43A047', glyph: 'leaf', examAreas: ['UPSC GS-I', 'UPSC GS-III'] },
  { key: 'health', label: 'Health', short: 'Health', gradient: ['#C62828', '#FF7A59'], color: '#E0473A', glyph: 'pulse', examAreas: ['UPSC GS-II', 'SSC GK'] },
  { key: 'sports', label: 'Sports & Awards', short: 'Sports', gradient: ['#D84E00', '#FFB300'], color: '#EF7A00', glyph: 'medal', examAreas: ['SSC GK', 'Railways GK'] },
  { key: 'history', label: 'History & Culture', short: 'History', gradient: ['#6D4C41', '#C08552'], color: '#9A6A4F', glyph: 'vase', examAreas: ['UPSC GS-I', 'SSC GK'] }
].map(Object.freeze));

export const CATEGORY_MAP = Object.freeze(Object.assign(Object.create(null), Object.fromEntries(CATEGORIES.map((c) => [c.key, c]))));

export function getCategory(key) {
  return typeof key === 'string' && Object.hasOwn(CATEGORY_MAP, key) ? CATEGORY_MAP[key] : CATEGORIES[0];
}
