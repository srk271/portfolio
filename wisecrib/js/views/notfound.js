import { html, mount } from '../dom.js';
import { icon } from '../ui/icons.js';

export function render(container) {
  mount(container, html`<div class="page"><div class="empty card" data-testid="not-found">
    ${icon('search')}
    <div class="empty-title">Not found</div>
    <p>That page doesn't exist in this demo.</p>
    <a class="btn btn-primary" href="#/feed">Go to feed</a>
  </div></div>`);
  return null;
}
