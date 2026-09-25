// Toasts + aria-live announcements. show({ message, kind, icon, action, duration }).
import { html, mount } from '../dom.js';
import { icon, coinIcon } from './icons.js';

const MAX = 3;

export function announce(message) {
  const live = document.getElementById('live');
  if (!live) return;
  live.textContent = '';
  // A fresh text node after a tick makes screen readers re-announce repeated messages.
  setTimeout(() => {
    live.textContent = message;
  }, 40);
}

const KIND_ICON = { success: 'checkCircle', warn: 'alert', error: 'alert', info: 'info' };

export function toast({ message, kind = 'info', iconName = null, action = null, duration = 3200, announceIt = true }) {
  const root = document.getElementById('toast-root');
  if (!root) return () => {};
  const el = document.createElement('div');
  el.className = `toast toast--${kind}`;
  el.dataset.testid = 'toast';
  const glyph = kind === 'coin' ? coinIcon() : icon(iconName || KIND_ICON[kind] || 'info');
  mount(el, html`${glyph}<span class="toast-msg">${message}</span>${action ? html`<button type="button" class="toast-action">${action.label}</button>` : ''}`);
  root.appendChild(el);
  while (root.children.length > MAX) root.firstElementChild.remove();

  let timer = null;
  const dismiss = () => {
    clearTimeout(timer);
    if (!el.isConnected) return;
    el.classList.add('is-leaving');
    setTimeout(() => el.remove(), 200);
  };
  if (action) {
    el.querySelector('.toast-action').addEventListener('click', () => {
      action.onClick?.();
      dismiss();
    });
  }
  timer = setTimeout(dismiss, action ? Math.max(duration, 6000) : duration);
  if (announceIt) announce(message);
  return dismiss;
}
