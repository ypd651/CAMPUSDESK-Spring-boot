import { get, patch, post, put } from '../api.js';
import { mountShell } from '../layout.js';
import {
  $, CATEGORY_LABELS, PRIORITY_LABELS, STATUS_FLOW, STATUS_LABELS, categoryLabel, confirmDialog, emptyHtml, esc,
  fieldHtml, formatDate, loadingHtml, openDialog, stagger, priorityBadge, selectHtml, setFormError, showFieldErrors,
  statusBadge, toast, withBusy,
} from '../ui.js';

const content = await mountShell('tickets', 'Ticket');
const id = Number(new URLSearchParams(window.location.search).get('id'));
let ticket = null;

if (content) {
  if (!Number.isInteger(id) || id <= 0) {
    content.innerHTML = emptyHtml('Ticket not found', 'The link is missing a valid ticket number.');
  } else {
    load();
  }
}

async function load() {
  content.innerHTML = loadingHtml('Loading ticket...');
  try {
    [ticket] = await Promise.all([get(`/api/tickets/${id}`)]);
    const [comments, history] = await Promise.all([get(`/api/tickets/${id}/comments`), get(`/api/tickets/${id}/history`)]);
    render(comments, history);
  } catch (error) {
    const title = error.status === 403 ? 'Access denied' : error.status === 404 ? 'Ticket not found' : 'Could not load the ticket';
    content.innerHTML = `${emptyHtml(title, error.message)}<p style="text-align:center"><a href="tickets.html">Back to tickets</a></p>`;
  }
}

function stepper() {
  const index = STATUS_FLOW.indexOf(ticket.status);
  return `<ol class="stepper" aria-label="Ticket progress">${STATUS_FLOW.map((s, i) =>
    `<li class="${i < index ? 'done' : i === index ? 'current' : ''}" ${i === index ? 'aria-current="step"' : ''}>${esc(STATUS_LABELS[s])}</li>`).join('')}</ol>`;
}

function render(comments, history) {
  const actions = ticket.availableActions;
  const has = (a) => actions.includes(a);

  content.innerHTML = `
    <div class="page-head">
      <div>
        <p><a href="tickets.html">Tickets</a> / #${ticket.id}</p>
        <h1>${esc(ticket.title)}</h1>
      </div>
      <div class="inline-actions">${statusBadge(ticket.status)} ${priorityBadge(ticket.priority)}</div>
    </div>
    <section class="card">${stepper()}</section>
    <div class="detail-grid" style="margin-top:18px">
      <div>
        <section class="card">
          <div class="card-head"><h2>Description</h2></div>
          <p class="description">${esc(ticket.description)}</p>
        </section>
        <section class="card" id="comments-card">
          <div class="card-head"><h2>Comments (${comments.length})</h2></div>
          ${comments.length ? comments.map(commentHtml).join('') : emptyHtml('No comments yet')}
          ${has('COMMENT') ? commentForm() : `<div class="locked" style="margin-top:14px">${ticket.status === 'CLOSED' ? 'This ticket is closed and no longer accepts comments.' : 'You can read the comments but not add new ones.'}</div>`}
        </section>
      </div>
      <div>
        <section class="card">
          <div class="card-head"><h2>Details</h2></div>
          <dl class="kv">
            <dt>Requester</dt><dd>${esc(ticket.requester.fullName)}<br><small>${esc(ticket.requester.email)}</small></dd>
            <dt>Technician</dt><dd>${ticket.technician ? `${esc(ticket.technician.fullName)}<br><small>${esc(ticket.technician.email)}</small>` : 'Not assigned yet'}</dd>
            <dt>Category</dt><dd>${esc(categoryLabel(ticket.category))}</dd>
            <dt>Created</dt><dd>${esc(formatDate(ticket.createdAt))}</dd>
            <dt>Updated</dt><dd>${esc(formatDate(ticket.updatedAt))}</dd>
          </dl>
        </section>
        ${actionsCard(has)}
        <section class="card">
          <div class="card-head"><h2>History</h2></div>
          <ul class="timeline">${history.slice().reverse().map(historyHtml).join('')}</ul>
        </section>
      </div>
    </div>`;
  stagger(content, '.timeline li');
  stagger(content, '.comment');
  bind(has);
}

const commentHtml = (c) => `
  <article class="comment">
    <header><strong>${esc(c.author.fullName)}</strong><span>${esc(formatDate(c.createdAt))}</span></header>
    <p>${esc(c.content)}</p>
  </article>`;

function commentForm() {
  return `<form id="comment-form" style="margin-top:14px" novalidate>
    <div class="field">
      <label for="comment-text">Add a comment</label>
      <textarea id="comment-text" name="content" maxlength="2000" style="min-height:90px" required></textarea>
      <span class="field-error"></span>
    </div>
    <div class="form-actions"><button class="btn primary" type="submit">Post comment</button></div>
  </form>`;
}

function historyHtml(h) {
  const change = h.previousStatus
    ? `${esc(STATUS_LABELS[h.previousStatus])} to ${esc(STATUS_LABELS[h.newStatus])}`
    : `Created as ${esc(STATUS_LABELS[h.newStatus])}`;
  return `<li><strong>${change}</strong>
    <span class="when">${esc(h.changedBy.fullName)} - ${esc(formatDate(h.changedAt))}</span>
    ${h.note ? `<span class="note">${esc(h.note)}</span>` : ''}</li>`;
}

function actionsCard(has) {
  const buttons = [];
  if (has('EDIT')) buttons.push('<button class="btn" data-act="edit" type="button">Edit request</button>');
  if (has('ASSIGN')) buttons.push(`<button class="btn primary" data-act="assign" type="button">${ticket.technician ? 'Reassign technician' : 'Assign technician'}</button>`);
  if (has('START')) buttons.push('<button class="btn primary" data-act="start" type="button">Start working</button>');
  if (has('RESOLVE')) buttons.push('<button class="btn primary" data-act="resolve" type="button">Mark as resolved</button>');
  if (has('CLOSE')) buttons.push('<button class="btn primary" data-act="close" type="button">Confirm and close</button>');
  if (!buttons.length) {
    const text = ticket.status === 'CLOSED' ? 'This ticket is closed.' : 'No actions available for you in this state.';
    return `<section class="card"><div class="card-head"><h2>Actions</h2></div><div class="locked">${text}</div></section>`;
  }
  return `<section class="card"><div class="card-head"><h2>Actions</h2></div><div class="actions-panel">${buttons.join('')}</div></section>`;
}

function bind() {
  content.querySelectorAll('[data-act]').forEach((button) =>
    button.addEventListener('click', () => handlers[button.dataset.act](button)));

  $('#comment-form')?.addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.target;
    const text = form.content.value.trim();
    setFormError(form, '');
    if (showFieldErrors(form, text ? null : { content: 'Write something before posting' })) return;
    try {
      await withBusy($('button[type=submit]', form), () => post(`/api/tickets/${id}/comments`, { content: text }));
      toast('Comment added', 'success');
      load();
    } catch (error) {
      if (!showFieldErrors(form, error.fieldErrors)) setFormError(form, error.message);
    }
  });
}

async function changeStatus(status, button, note) {
  try {
    await withBusy(button, () => patch(`/api/tickets/${id}/status`, { status, note }));
    toast(`Ticket is now ${STATUS_LABELS[status].toLowerCase()}`, 'success');
  } catch (error) {
    toast(error.message, 'error');
  }
  load();
}

const handlers = {
  start: (button) => changeStatus('IN_PROGRESS', button),
  resolve: (button) => {
    const dialog = openDialog(`
      <h2>Mark as resolved</h2>
      <form id="resolve-form">
        <div class="field">
          <label for="f-note">What was done? (optional)</label>
          <textarea id="f-note" name="note" maxlength="255" style="min-height:90px"></textarea>
        </div>
        <div class="form-actions">
          <button class="btn" type="button" data-close>Cancel</button>
          <button class="btn primary" type="submit">Mark as resolved</button>
        </div>
      </form>`);
    dialog.querySelector('[data-close]').addEventListener('click', () => dialog.close());
    dialog.querySelector('#resolve-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const note = e.target.note.value.trim() || undefined;
      dialog.close();
      await changeStatus('RESOLVED', button, note);
    });
  },
  close: async (button) => {
    const ok = await confirmDialog('Close this ticket?', 'Confirm that the problem is solved. A closed ticket cannot be changed or commented.', 'Close ticket');
    if (ok) changeStatus('CLOSED', button);
  },
  edit: () => {
    const dialog = openDialog(`
      <h2>Edit request</h2>
      <form id="edit-form" novalidate>
        ${fieldHtml({ name: 'title', label: 'Title', value: ticket.title })}
        <div class="field">
          <label for="f-description">Description</label>
          <textarea id="f-description" name="description" maxlength="4000" required>${esc(ticket.description)}</textarea>
          <span class="field-error"></span>
        </div>
        <div class="row-2">
          ${selectHtml({ name: 'category', label: 'Category', options: Object.entries(CATEGORY_LABELS), value: ticket.category })}
          ${selectHtml({ name: 'priority', label: 'Priority', options: Object.entries(PRIORITY_LABELS), value: ticket.priority })}
        </div>
        <div class="form-actions">
          <button class="btn" type="button" data-close>Cancel</button>
          <button class="btn primary" type="submit">Save changes</button>
        </div>
      </form>`);
    dialog.querySelector('[data-close]').addEventListener('click', () => dialog.close());
    dialog.querySelector('#edit-form').addEventListener('submit', async (event) => {
      event.preventDefault();
      const form = event.target;
      const body = {
        title: form.title.value.trim(), description: form.description.value.trim(),
        category: form.category.value, priority: form.priority.value,
      };
      setFormError(form, '');
      try {
        await withBusy($('button[type=submit]', form), () => put(`/api/tickets/${id}`, body));
        toast('Request updated', 'success');
        dialog.close();
        load();
      } catch (error) {
        if (!showFieldErrors(form, error.fieldErrors)) setFormError(form, error.message);
      }
    });
  },
  assign: async () => {
    let technicians;
    try {
      technicians = await get('/api/users/technicians');
    } catch (error) {
      toast(error.message, 'error');
      return;
    }
    if (!technicians.length) {
      toast('There are no technicians yet. Create one in Administration > Users.', 'error');
      return;
    }
    const dialog = openDialog(`
      <h2>${ticket.technician ? 'Reassign technician' : 'Assign technician'}</h2>
      <form id="assign-form">
        ${selectHtml({
          name: 'technicianId', label: 'Technician',
          options: technicians.map((t) => [String(t.id), `${t.fullName} (${t.email})`]),
          value: ticket.technician ? String(ticket.technician.id) : '',
        })}
        <div class="form-actions">
          <button class="btn" type="button" data-close>Cancel</button>
          <button class="btn primary" type="submit">Save assignment</button>
        </div>
      </form>`);
    dialog.querySelector('[data-close]').addEventListener('click', () => dialog.close());
    dialog.querySelector('#assign-form').addEventListener('submit', async (event) => {
      event.preventDefault();
      const form = event.target;
      try {
        await withBusy($('button[type=submit]', form), () =>
          patch(`/api/tickets/${id}/assign`, { technicianId: Number(form.technicianId.value) }));
        toast('Technician assigned', 'success');
        dialog.close();
        load();
      } catch (error) {
        setFormError(form, error.message);
      }
    });
  },
};
