// Tiny pub/sub. on(event, fn) returns an unsubscribe function.
// Events: state:changed, store:reset, coins:awarded, reward:rejected, toast, premium:changed,
//         route:changed, view:rendered, theme:changed, header:title, install:available
const handlers = new Map();

export function on(event, fn) {
  if (!handlers.has(event)) handlers.set(event, new Set());
  handlers.get(event).add(fn);
  return () => handlers.get(event)?.delete(fn);
}

export function emit(event, payload) {
  const set = handlers.get(event);
  if (!set) return;
  for (const fn of [...set]) {
    try {
      fn(payload);
    } catch (err) {
      console.error(`[bus] handler for "${event}" failed`, err);
    }
  }
}
