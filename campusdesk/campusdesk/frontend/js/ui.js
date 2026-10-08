// Small UI helpers shared by every page.
export const $ = (selector, root = document) => root.querySelector(selector);
export const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

/** Escapes text before it is placed inside innerHTML. */
export function esc(value) {
  return String(value ?? '').replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]));
}

export const STATUS_LABELS = {
  OPEN: 'Open', ASSIGNED: 'Assigned', IN_PROGRESS: 'In progress', RESOLVED: 'Resolved', CLOSED: 'Closed',
};
export const PRIORITY_LABELS = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High', CRITICAL: 'Critical' };
export const CATEGORY_LABELS = {
  HARDWARE: 'Hardware', SOFTWARE: 'Software', NETWORK: 'Network', ACCESS: 'Access', OTHER: 'Other',
};
export const STATUS_FLOW = ['OPEN', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];

export const statusBadge = (s) => `<span class="badge ${esc(s)}">${esc(STATUS_LABELS[s] || s)}</span>`;
export const priorityBadge = (p) => `<span class="badge ${esc(p)}">${esc(PRIORITY_LABELS[p] || p)}</span>`;
export const categoryLabel = (c) => CATEGORY_LABELS[c] || c;

const dateTime = new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' });
export const formatDate = (iso) => (iso ? dateTime.format(new Date(iso)) : '-');

export function toast(message, type = 'info') {
  let box = $('#toasts');
  if (!box) {
    box = document.createElement('div');
    box.id = 'toasts';
    box.setAttribute('aria-live', 'polite');
    document.body.append(box);
  }
  const el = document.createElement('div');
  el.className = `toast ${type}`;
  el.textContent = message;
  box.append(el);
  setTimeout(() => {
    el.classList.add('leaving');
    setTimeout(() => el.remove(), 260);
  }, type === 'error' ? 6000 : 3500);
}

/** Animates a number from 0 to its final value. */
export function countUp(element, target, duration = 800) {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches || target === 0) {
    element.textContent = target;
    return;
  }
  const start = performance.now();
  const tick = (now) => {
    const t = Math.min(1, (now - start) / duration);
    element.textContent = Math.round(target * (1 - (1 - t) ** 3));
    if (t < 1) requestAnimationFrame(tick);
  };
  requestAnimationFrame(tick);
}

/** Gives each matching child an --i index so CSS can stagger its entrance animation. */
export function stagger(root, selector) {
  $$(selector, root).forEach((el, i) => el.style.setProperty('--i', i));
}

export const skeletonStats = (count = 6) =>
  `<section class="stats" aria-hidden="true">${'<div class="skeleton stat-sk"></div>'.repeat(count)}</section>`;

export const skeletonRows = (count = 5) =>
  `<div aria-hidden="true">${'<div class="skeleton line"></div>'.repeat(count)}</div>`;

export const loadingHtml = (text = 'Loading...') =>
  `<div class="loading" role="status"><span class="spin"></span><span>${esc(text)}</span></div>`;

export const emptyHtml = (title, text = '') =>
  `<div class="empty"><strong>${esc(title)}</strong>${text ? `<span>${esc(text)}</span>` : ''}</div>`;

/** Disables a button and shows a spinner while an async action runs. */
export async function withBusy(button, task) {
  if (!button) return task();
  const original = button.innerHTML;
  button.disabled = true;
  button.innerHTML = `<span class="spin"></span> ${original}`;
  try {
    return await task();
  } finally {
    button.disabled = false;
    button.innerHTML = original;
  }
}

/** Shows server-side field errors under the matching inputs (inputs need a name attribute). */
export function showFieldErrors(form, fieldErrors) {
  $$('.field', form).forEach((f) => f.classList.remove('invalid'));
  $$('.field-error', form).forEach((e) => (e.textContent = ''));
  if (!fieldErrors) return false;
  let any = false;
  for (const [name, message] of Object.entries(fieldErrors)) {
    const input = form.elements[name];
    const field = input?.closest?.('.field');
    if (!field) continue;
    field.classList.add('invalid');
    const slot = $('.field-error', field);
    if (slot) slot.textContent = message;
    any = true;
  }
  return any;
}

export function setFormError(form, message) {
  let box = $('.form-error', form);
  if (!message) {
    box?.remove();
    return;
  }
  if (!box) {
    box = document.createElement('div');
    box.className = 'form-error';
    box.setAttribute('role', 'alert');
    form.prepend(box);
  }
  box.textContent = message;
}

export function openDialog(html) {
  const dialog = document.createElement('dialog');
  dialog.className = 'modal';
  dialog.innerHTML = `<div class="modal-body">${html}</div>`;
  document.body.append(dialog);
  dialog.addEventListener('close', () => dialog.remove());
  dialog.showModal();
  return dialog;
}

export function confirmDialog(title, message, confirmLabel = 'Confirm', danger = false) {
  return new Promise((resolve) => {
    const dialog = openDialog(`
      <h2>${esc(title)}</h2>
      <p>${esc(message)}</p>
      <div class="form-actions">
        <button type="button" class="btn" data-answer="no">Cancel</button>
        <button type="button" class="btn ${danger ? 'danger' : 'primary'}" data-answer="yes">${esc(confirmLabel)}</button>
      </div>`);
    let answer = false;
    dialog.addEventListener('click', (e) => {
      const choice = e.target.closest('[data-answer]')?.dataset.answer;
      if (!choice) return;
      answer = choice === 'yes';
      dialog.close();
    });
    dialog.addEventListener('close', () => resolve(answer));
  });
}

export function fieldHtml({ name, label, type = 'text', value = '', hint = '', required = true, autocomplete = 'off' }) {
  return `<div class="field">
    <label for="f-${esc(name)}">${esc(label)}</label>
    <input id="f-${esc(name)}" name="${esc(name)}" type="${esc(type)}" value="${esc(value)}" autocomplete="${esc(autocomplete)}" ${required ? 'required' : ''}>
    ${hint ? `<span class="hint">${esc(hint)}</span>` : ''}
    <span class="field-error"></span>
  </div>`;
}

export function selectHtml({ name, label, options, value = '' }) {
  const items = options.map(([v, text]) =>
    `<option value="${esc(v)}" ${v === value ? 'selected' : ''}>${esc(text)}</option>`).join('');
  return `<div class="field">
    <label for="f-${esc(name)}">${esc(label)}</label>
    <select id="f-${esc(name)}" name="${esc(name)}">${items}</select>
    <span class="field-error"></span>
  </div>`;
}

export const STRONG_PASSWORD = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,72}$/;
export const PASSWORD_HINT = '8+ characters with upper case, lower case, a number and a symbol.';
