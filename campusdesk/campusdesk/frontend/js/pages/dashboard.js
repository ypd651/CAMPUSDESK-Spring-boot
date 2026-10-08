import { get } from '../api.js';
import { mountShell } from '../layout.js';
import { can } from '../session.js';
import { PRIORITY_LABELS, countUp, emptyHtml, esc, formatDate, skeletonRows, skeletonStats, stagger, statusBadge, toast } from '../ui.js';

const content = await mountShell('dashboard', 'Dashboard');
if (content) render();

async function render() {
  const user = JSON.parse(sessionStorage.getItem('campusdesk.user'));
  const global = can('TICKET_READ_ALL');
  const first = user.fullName.split(' ')[0];
  const canCreate = can('TICKET_CREATE');

  content.innerHTML = `
    <div class="page-head">
      <div>
        <h1>Hello, ${esc(first)}</h1>
        <p>${global ? 'Overview of every incident in the company.' : 'Overview of the tickets you can access.'}</p>
      </div>
      ${canCreate ? '<a class="btn primary" href="tickets.html?new=1">New request</a>' : ''}
    </div>
    <div id="stats">${skeletonStats()}</div>
    <div class="two-col" id="lower"><section class="card">${skeletonRows(6)}</section><section class="card">${skeletonRows(4)}</section></div>`;

  try {
    const [summary, tickets] = await Promise.all([get('/api/reports/summary'), get('/api/tickets')]);
    renderStats(summary);
    renderLower(summary, tickets);
  } catch (error) {
    document.getElementById('stats').innerHTML = emptyHtml('Could not load the dashboard', error.message);
    toast(error.message, 'error');
  }
}

function renderStats(s) {
  const items = [
    ['total', s.total, 'Total tickets'], ['open', s.open, 'Open'], ['assigned', s.assigned, 'Assigned'],
    ['progress', s.inProgress, 'In progress'], ['resolved', s.resolved, 'Resolved'], ['closed', s.closed, 'Closed'],
  ];
  document.getElementById('stats').innerHTML = `
    <section class="stats" aria-label="Ticket indicators">${items.map(([cls, value, label], i) => `
      <div class="stat ${cls}" style="--i:${i}"><div class="value" data-value="${value}">0</div><div class="label">${label}</div></div>`).join('')}
    </section>`;
  document.querySelectorAll('#stats .value').forEach((el) => countUp(el, Number(el.dataset.value)));
}

function renderLower(summary, tickets) {
  const recent = tickets.slice(0, 6);
  const max = Math.max(1, ...Object.values(summary.byPriority));
  const bars = Object.entries(summary.byPriority).reverse().map(([priority, count]) => `
    <div class="bar-row">
      <span>${esc(PRIORITY_LABELS[priority] || priority)}</span>
      <div class="bar-track"><div class="bar-fill ${esc(priority)}" style="width:0" data-width="${(count / max) * 100}"></div></div>
      <strong>${count}</strong>
    </div>`).join('');

  const list = recent.length
    ? `<ul class="mini-list">${recent.map((t) => `
        <li>
          <div>
            <a href="ticket-detail.html?id=${t.id}">${esc(t.title)}</a>
            <small>#${t.id} - ${esc(t.requester.fullName)} - ${esc(formatDate(t.updatedAt))}</small>
          </div>
          ${statusBadge(t.status)}
        </li>`).join('')}</ul>`
    : emptyHtml('No tickets yet', can('TICKET_CREATE') ? 'Create your first request to get started.' : 'Nothing to show for your role right now.');

  document.getElementById('lower').innerHTML = `
    <section class="card">
      <div class="card-head"><h2>Latest activity</h2><a href="tickets.html">View all</a></div>
      ${list}
    </section>
    <section class="card">
      <div class="card-head"><h2>By priority</h2></div>
      <div class="bars">${bars}</div>
    </section>`;
  stagger(document.getElementById('lower'), '.mini-list li');
  requestAnimationFrame(() => requestAnimationFrame(() =>
    document.querySelectorAll('.bar-fill').forEach((bar) => { bar.style.width = `${bar.dataset.width}%`; })));
}
