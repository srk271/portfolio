// Bottom sheet / modal. openSheet({ title, body, actions, onMount, dismissible }) -> Promise<value|null>.
// Focus is trapped inside, Esc and the backdrop dismiss (when dismissible), focus returns to the opener.
// Actions: { label, value, variant, testid, onSelect(api) } — onSelect may return false to keep it open.
import { html, mount } from '../dom.js';
import { uid } from '../format.js';
import { icon } from './icons.js';

let active = null;

const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

export function closeActiveSheet() {
  active?.close(null);
}

export const hasActiveSheet = () => active !== null;

export function openSheet({ title, body = '', actions = [], onMount = null, dismissible = true, testid = 'sheet' }) {
  if (active) active.close(null);
  return new Promise((resolve) => {
    const root = document.getElementById('sheet-root');
    const opener = document.activeElement;
    const titleId = uid('sheet-title');
    const wrap = document.createElement('div');
    wrap.className = 'sheet-wrap';
    mount(wrap, html`<div class="sheet-backdrop" data-sheet-dismiss></div>
      <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="${titleId}" data-testid="${testid}">
        <div class="sheet-handle" aria-hidden="true"></div>
        <div class="sheet-head">
          <h2 class="sheet-title" id="${titleId}">${title}</h2>
          ${dismissible ? html`<button type="button" class="icon-btn" data-sheet-dismiss aria-label="Close">${icon('close')}</button>` : ''}
        </div>
        <div class="sheet-body">${body}</div>
        <div class="sheet-actions">${actions.map((a, i) => html`<button type="button" class="btn ${a.variant || 'btn-secondary'} btn-block" data-sheet-action="${i}" ${a.testid ? html`data-testid="${a.testid}"` : ''}>${a.label}</button>`)}</div>
      </div>`);
    if (!actions.length) wrap.querySelector('.sheet-actions').remove();
    root.appendChild(wrap);

    const shellParts = ['app-header', 'view', 'tabbar'].map((id) => document.getElementById(id)).filter(Boolean);
    shellParts.forEach((el) => {
      el.inert = true;
    });

    const sheetEl = wrap.querySelector('.sheet');
    let closed = false;
    let busy = false;

    const onKey = (e) => {
      if (e.key === 'Escape' && dismissible && !busy) {
        e.preventDefault();
        api.close(null);
      } else if (e.key === 'Tab') {
        const items = [...sheetEl.querySelectorAll(FOCUSABLE)].filter((el) => el.offsetParent !== null);
        if (!items.length) return;
        const first = items[0];
        const last = items[items.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    };

    const onClick = async (e) => {
      const target = e.target instanceof Element ? e.target : null;
      if (!target || busy) return;
      if (target.closest('[data-sheet-dismiss]')) {
        if (dismissible) api.close(null);
        return;
      }
      const btn = target.closest('[data-sheet-action]');
      if (!btn) return;
      const action = actions[Number(btn.dataset.sheetAction)];
      if (!action) return;
      if (action.onSelect) {
        const result = await action.onSelect(api);
        if (result === false) return;
        api.close(result === undefined || result === true ? action.value ?? true : result);
      } else {
        api.close(action.value ?? true);
      }
    };

    const api = {
      el: sheetEl,
      body: wrap.querySelector('.sheet-body'),
      setBody(template) {
        mount(api.body, template);
      },
      setBusy(value) {
        busy = Boolean(value);
        sheetEl.querySelectorAll('[data-sheet-action], [data-sheet-dismiss]').forEach((b) => {
          if (b.tagName === 'BUTTON') b.disabled = busy;
        });
      },
      setActions(visible) {
        const bar = sheetEl.querySelector('.sheet-actions');
        if (bar) bar.hidden = !visible;
      },
      close(value) {
        if (closed) return;
        closed = true;
        document.removeEventListener('keydown', onKey, true);
        wrap.remove(); // removed synchronously so a follow-up sheet never overlaps this one
        shellParts.forEach((el) => {
          el.inert = false;
        });
        if (active === api) active = null;
        if (opener && opener.isConnected && typeof opener.focus === 'function') opener.focus({ preventScroll: true });
        resolve(value);
      }
    };
    active = api;
    document.addEventListener('keydown', onKey, true);
    wrap.addEventListener('click', onClick);
    onMount?.(api);
    const first = sheetEl.querySelector('.sheet-body input, .sheet-body button, .sheet-actions button') || sheetEl.querySelector(FOCUSABLE);
    (first || sheetEl).focus?.({ preventScroll: true });
  });
}

export function confirmSheet({ title, body, confirmLabel = 'Confirm', cancelLabel = 'Cancel', variant = 'btn-primary', testid = 'confirm-sheet' }) {
  return openSheet({
    title,
    body,
    testid,
    actions: [
      { label: confirmLabel, value: true, variant, testid: 'sheet-confirm' },
      { label: cancelLabel, value: false, variant: 'btn-ghost', testid: 'sheet-cancel' }
    ]
  }).then((v) => v === true);
}
