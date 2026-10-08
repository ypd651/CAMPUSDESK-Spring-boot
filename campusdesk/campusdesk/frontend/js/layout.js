import { can, canAny, getUser, logout, requireSession, updateUser } from './session.js';
import { get } from './api.js';
import { $, esc, toast } from './ui.js';

const ICONS = {
  dashboard: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="9" rx="1.5"/><rect x="14" y="3" width="7" height="5" rx="1.5"/><rect x="14" y="12" width="7" height="9" rx="1.5"/><rect x="3" y="16" width="7" height="5" rx="1.5"/></svg>',
  tickets: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 9a2 2 0 0 0 0 6v3a1 1 0 0 0 1 1h16a1 1 0 0 0 1-1v-3a2 2 0 0 1 0-6V6a1 1 0 0 0-1-1H4a1 1 0 0 0-1 1z"/><path d="M13 5v14" stroke-dasharray="2 3"/></svg>',
  admin: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3 4 6v6c0 4.5 3.2 7.6 8 9 4.8-1.4 8-4.5 8-9V6z"/><path d="m9 12 2 2 4-4"/></svg>',
};

window.addEventListener('error', (event) => toast(`Script error: ${event.message}`, 'error'));
window.addEventListener('unhandledrejection', (event) => toast(`Error: ${event.reason?.message || event.reason}`, 'error'));
window.addEventListener('pageshow', () => document.body.classList.remove('leaving'));

export const LOGO = `<svg viewBox="0 0 40 40" aria-hidden="true"><rect width="40" height="40" rx="10" fill="#1b7f79"/><path d="M11 14a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v3.2a2.8 2.8 0 0 0 0 5.6V26a2 2 0 0 1-2 2H13a2 2 0 0 1-2-2v-3.2a2.8 2.8 0 0 0 0-5.6z" fill="none" stroke="#fff" stroke-width="2"/><path d="m16.5 20 2.6 2.6 4.9-5.2" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>`;

function navItems() {
  const items = [{ key: 'dashboard', label: 'Dashboard', href: 'dashboard.html' }];
  if (canAny('TICKET_READ_ALL', 'TICKET_READ_OWN', 'TICKET_READ_ASSIGNED')) {
    items.push({ key: 'tickets', label: 'Tickets', href: 'tickets.html' });
  }
  if (canAny('TICKET_ASSIGN', 'USERS_READ', 'USERS_MANAGE', 'ROLES_MANAGE')) {
    items.push({ key: 'admin', label: 'Administration', href: 'admin.html' });
  }
  return items;
}

/**
 * Builds the application shell (sidebar, top bar on mobile) and returns the content container.
 * Also refreshes the user's permissions from the API so role changes apply without logging in again.
 */
export async function mountShell(active, title) {
  if (!requireSession()) return null;
  document.title = `${title} - CampusDesk`;

  try {
    updateUser(await get('/api/auth/me'));
  } catch {
    // The api() helper already redirects on 401; other errors keep the cached session.
  }
  const user = getUser();
  if (!user) return null;

  const links = navItems().map((item) => `
    <a href="${item.href}" ${item.key === active ? 'aria-current="page"' : ''}>${ICONS[item.key]}<span>${esc(item.label)}</span></a>`).join('');

  document.body.innerHTML = `
    <div class="shell">
      <aside class="sidebar" id="sidebar" aria-label="Main navigation">
        <a class="logo" href="dashboard.html" style="text-decoration:none">${LOGO}<span>CampusDesk</span></a>
        <nav class="nav">${links}</nav>
        <div class="user-card">
          <span class="name">${esc(user.fullName)}</span>
          <span class="meta">${esc(user.email)}</span>
          <span class="meta">Role: ${esc(user.role)}</span>
          <button class="btn sm" id="logout-btn" type="button">Sign out</button>
        </div>
      </aside>
      <div class="main">
        <header class="topbar">
          <button id="menu-btn" type="button" aria-label="Open menu" aria-expanded="false">Menu</button>
          <strong>${esc(title)}</strong>
        </header>
        <main class="content" id="content"></main>
      </div>
      <div class="scrim" id="scrim"></div>
    </div>
    <div id="toasts" aria-live="polite"></div>`;

  const setMenu = (open) => {
    document.body.classList.toggle('menu-open', open);
    $('#menu-btn').setAttribute('aria-expanded', String(open));
  };
  $('#menu-btn').addEventListener('click', () => setMenu(!document.body.classList.contains('menu-open')));
  $('#scrim').addEventListener('click', () => setMenu(false));
  $('#logout-btn').addEventListener('click', logout);
  document.querySelectorAll('.nav a').forEach((link) => link.addEventListener('click', (event) => {
    if (link.getAttribute('aria-current') === 'page' || event.metaKey || event.ctrlKey) return;
    event.preventDefault();
    document.body.classList.add('leaving');
    setTimeout(() => { window.location.href = link.href; }, 160);
  }));
  return $('#content');
}

export { can };
