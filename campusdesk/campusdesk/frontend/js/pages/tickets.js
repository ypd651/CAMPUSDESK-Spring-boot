import { get, post } from '../api.js';
import { mountShell } from '../layout.js';
import { can } from '../session.js';
import {
  $, CATEGORY_LABELS, PRIORITY_LABELS, STATUS_LABELS, categoryLabel, emptyHtml, esc, fieldHtml, formatDate, localToday,
  skeletonRows, openDialog, priorityBadge, selectHtml, setFormError, showFieldErrors, stagger, statusBadge, toast, withBusy,
} from '../ui.js';

const content = await mountShell('tickets', 'Tickets');
// Everything is filtered, sorted and paginated by the backend (GET /api/tickets/search).
const filters = { status: '', priority: '', category: '', createdFrom: '', createdTo: '', q: '' };
const view = { sort: 'createdAt,desc', page: 0, size: 10 };
let current = null;   // last page returned by the API

const SORT_OPTIONS = [
  ['createdAt,desc', 'Newest first'], ['createdAt,asc', 'Oldest first'], ['updatedAt,desc', 'Recently updated'],
  ['dueDate,asc', 'Due date (soonest)'], ['priority,asc', 'Priority (A-Z)'], ['status,asc', 'Status (A-Z)'], ['title,asc', 'Title (A-Z)'],
];

const options = (labels, allLabel) => [['', allLabel], ...Object.entries(labels)];

function renderPage() {
  const scope = can('TICKET_READ_ALL') ? 'Every ticket in the company.' : 'Tickets you created or that are assigned to you.';
  content.innerHTML = `
    <div class="page-head">
      <div><h1>Tickets</h1><p>${esc(scope)}</p></div>
      ${can('TICKET_CREATE') ? '<button class="btn primary" id="new-ticket" type="button">New request</button>' : ''}
    </div>
    <section class="card">
      <form class="filters" id="filters" aria-label="Ticket filters">
        <div class="field search-field">
          <label for="f-q">Search</label>
          <input id="f-q" name="q" type="search" placeholder="Title or description..." autocomplete="off">
        </div>
        ${selectHtml({ name: 'status', label: 'Status', options: options(STATUS_LABELS, 'All statuses') })}
        ${selectHtml({ name: 'priority', label: 'Priority', options: options(PRIORITY_LABELS, 'All priorities') })}
        ${selectHtml({ name: 'category', label: 'Category', options: options(CATEGORY_LABELS, 'All categories') })}
        <div class="field"><label for="f-from">Created from</label><input id="f-from" name="createdFrom" type="date"></div>
        <div class="field"><label for="f-to">Created to</label><input id="f-to" name="createdTo" type="date"></div>
        ${selectHtml({ name: 'sort', label: 'Sort by', options: SORT_OPTIONS, value: view.sort })}
        <button class="btn" type="button" id="clear-filters">Clear filters</button>
      </form>
      <div id="list"></div>
    </section>`;

  $('#filters').addEventListener('submit', (e) => e.preventDefault());
  $('#filters').addEventListener('change', (e) => {
    if (e.target.name === 'q') return;
    if (e.target.name === 'sort') view.sort = e.target.value;
    else filters[e.target.name] = e.target.value;
    view.page = 0;
    loadTickets();
  });
  let timer;
  $('#f-q').addEventListener('input', (e) => {
    clearTimeout(timer);
    timer = setTimeout(() => { filters.q = e.target.value.trim(); view.page = 0; loadTickets(); }, 350);
  });
  $('#clear-filters').addEventListener('click', () => {
    $('#filters').reset();
    Object.keys(filters).forEach((k) => (filters[k] = ''));
    view.sort = 'createdAt,desc';
    view.page = 0;
    loadTickets();
  });
  $('#new-ticket')?.addEventListener('click', openCreateDialog);
}

async function loadTickets() {
  const list = $('#list');
  list.innerHTML = skeletonRows(6);
  const [sort, direction] = view.sort.split(',');
  const params = new URLSearchParams(Object.entries(filters).filter(([, v]) => v));
  params.set('sort', sort);
  params.set('direction', direction);
  params.set('page', view.page);
  params.set('size', view.size);
  try {
    current = await get(`/api/tickets/search?${params}`);
    paint();
  } catch (error) {
    list.innerHTML = emptyHtml('Could not load the tickets', error.message);
    toast(error.message, 'error');
  }
}

function paint() {
  const list = $('#list');
  const tickets = current.content;
  if (!tickets.length) {
    const filtered = Object.values(filters).some(Boolean);
    list.innerHTML = emptyHtml(filtered ? 'No tickets match your search' : 'No tickets yet',
      filtered ? 'Try another word or clear the filters.' : (can('TICKET_CREATE') ? 'Create a new request to see it here.' : ''));
    return;
  }
  const from = current.page * current.size + 1;
  const to = from + tickets.length - 1;
  list.innerHTML = `
    <div class="table-wrap">
      <table class="stack">
        <thead><tr><th>#</th><th>Title</th><th>Category</th><th>Priority</th><th>Status</th><th>Requester</th><th>Technician</th><th>Updated</th></tr></thead>
        <tbody>${tickets.map(rowHtml).join('')}</tbody>
      </table>
    </div>
    <div class="pager">
      <span class="id">Showing ${from}-${to} of ${current.totalElements} ticket(s)</span>
      <div class="inline-actions">
        <button class="btn sm" type="button" id="prev-page" ${current.first ? 'disabled' : ''}>Previous</button>
        <span class="id">Page ${current.page + 1} of ${Math.max(1, current.totalPages)}</span>
        <button class="btn sm" type="button" id="next-page" ${current.last ? 'disabled' : ''}>Next</button>
      </div>
    </div>`;
  stagger(list, 'tbody tr');
  $('#prev-page').addEventListener('click', () => { view.page -= 1; loadTickets(); });
  $('#next-page').addEventListener('click', () => { view.page += 1; loadTickets(); });
  list.querySelectorAll('tr.clickable').forEach((row) => {
    row.addEventListener('click', (e) => {
      if (!e.target.closest('a')) window.location.href = `ticket-detail.html?id=${row.dataset.id}`;
    });
  });
}

function rowHtml(t) {
  return `<tr class="clickable row-in" data-id="${t.id}">
    <td data-label="#" class="id">${t.id}</td>
    <td data-label="Title"><a class="title" href="ticket-detail.html?id=${t.id}">${esc(t.title)}</a></td>
    <td data-label="Category">${esc(categoryLabel(t.category))}</td>
    <td data-label="Priority">${priorityBadge(t.priority)}</td>
    <td data-label="Status">${statusBadge(t.status)}</td>
    <td data-label="Requester">${esc(t.requester.fullName)}</td>
    <td data-label="Technician">${t.technician ? esc(t.technician.fullName) : '<span class="id">Unassigned</span>'}</td>
    <td data-label="Updated">${esc(formatDate(t.updatedAt))}</td>
  </tr>`;
}

function openCreateDialog() {
  const dialog = openDialog(`
    <h2>New support request</h2>
    <form id="ticket-form" novalidate>
      ${fieldHtml({ name: 'title', label: 'Title', hint: 'A short, descriptive name for the problem (5-150 characters).' })}
      <div class="field">
        <label for="f-description">Description</label>
        <textarea id="f-description" name="description" maxlength="4000" required></textarea>
        <span class="hint">What happens, since when, and what you already tried (10+ characters).</span>
        <span class="field-error"></span>
      </div>
      <div class="row-2">
        ${selectHtml({ name: 'category', label: 'Category', options: Object.entries(CATEGORY_LABELS) })}
        ${selectHtml({ name: 'priority', label: 'Priority', options: Object.entries(PRIORITY_LABELS), value: 'MEDIUM' })}
      </div>
      <div class="field">
        <label for="f-dueDate">Needed by (optional)</label>
        <input id="f-dueDate" name="dueDate" type="date" min="${localToday()}">
        <span class="field-error"></span>
      </div>
      <div class="form-actions">
        <button class="btn" type="button" data-close>Cancel</button>
        <button class="btn primary" type="submit">Send request</button>
      </div>
    </form>`);

  dialog.querySelector('[data-close]').addEventListener('click', () => dialog.close());
  dialog.querySelector('#ticket-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.target;
    const body = {
      title: form.title.value.trim(),
      description: form.description.value.trim(),
      category: form.category.value,
      priority: form.priority.value,
      dueDate: form.dueDate.value || null,
    };
    const errors = {};
    if (body.title.length < 5) errors.title = 'Title must have at least 5 characters';
    if (body.description.length < 10) errors.description = 'Description must have at least 10 characters';
    setFormError(form, '');
    if (showFieldErrors(form, errors)) return;

    try {
      const ticket = await withBusy($('button[type=submit]', form), () => post('/api/tickets', body));
      toast(`Request #${ticket.id} created`, 'success');
      dialog.close();
      loadTickets();
    } catch (error) {
      if (!showFieldErrors(form, error.fieldErrors)) setFormError(form, error.message);
    }
  });
}

// Start the page only after every declaration above has been evaluated.
if (content) {
  renderPage();
  loadTickets();
  if (new URLSearchParams(window.location.search).has('new') && can('TICKET_CREATE')) openCreateDialog();
}
