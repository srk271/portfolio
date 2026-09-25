// Safe templating. html`` escapes every interpolated value unless it is a Raw
// (from raw() or a nested html`` result). Arrays render item by item with the same rule.
// null / undefined / false render as nothing. mount() is the only place innerHTML is assigned.

export class Raw {
  constructor(value) {
    this.value = String(value);
  }
  toString() {
    return this.value;
  }
}

const ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

export function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (ch) => ESCAPES[ch]);
}

export function raw(value) {
  return value instanceof Raw ? value : new Raw(value ?? '');
}

function renderValue(value) {
  if (value === null || value === undefined || value === false) return '';
  if (value instanceof Raw) return value.value;
  if (Array.isArray(value)) return value.map(renderValue).join('');
  return escapeHtml(value);
}

export function html(strings, ...values) {
  let out = strings[0];
  for (let i = 0; i < values.length; i += 1) {
    out += renderValue(values[i]) + strings[i + 1];
  }
  return new Raw(out);
}

export function mount(el, template) {
  if (!(template instanceof Raw)) throw new TypeError('mount() only accepts html`` templates');
  el.innerHTML = template.value;
  return el;
}

export const qs = (selector, root = document) => root.querySelector(selector);
export const qsa = (selector, root = document) => Array.from(root.querySelectorAll(selector));

export function on(el, event, fn, options) {
  el.addEventListener(event, fn, options);
  return () => el.removeEventListener(event, fn, options);
}

export function delegate(root, event, selector, fn) {
  const handler = (e) => {
    const target = e.target instanceof Element ? e.target.closest(selector) : null;
    if (target && root.contains(target)) fn(e, target);
  };
  root.addEventListener(event, handler);
  return () => root.removeEventListener(event, handler);
}

// Collects cleanup functions so views can return a single disposer.
export function disposer() {
  const fns = [];
  const dispose = () => {
    while (fns.length) {
      try {
        fns.pop()();
      } catch (err) {
        console.error(err);
      }
    }
  };
  dispose.add = (fn) => {
    if (typeof fn === 'function') fns.push(fn);
    return fn;
  };
  return dispose;
}
