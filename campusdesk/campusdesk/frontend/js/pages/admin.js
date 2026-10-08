import { del, get, patch, post, put } from '../api.js';
import { mountShell } from '../layout.js';
import { can, getUser } from '../session.js';
import {
  $, $$, PASSWORD_HINT, STATUS_LABELS, STRONG_PASSWORD, categoryLabel, confirmDialog, emptyHtml, esc, fieldHtml,
  formatDate, loadingHtml, openDialog, priorityBadge, selectHtml, setFormError, showFieldErrors, statusBadge, toast, withBusy,
} from '../ui.js';

const content = await mountShell('admin', 'Administration');

const TABS = [
  { key: 'tickets', label: 'Tickets', show: () => can('TICKET_ASSIGN') && can('TICKET_READ_ALL'), load: loadTickets },
  { key: 'technicians', label: 'Technicians', show: () => can('TICKET_ASSIGN'), load: loadTechnicians },
  { key: 'users', label: 'Users', show: () => can('USERS_READ'), load: loadUsers },
  { key: 'roles', label: 'Roles', show: () => can('ROLES_MANAGE'), load: loadRoles },
];

if (content) {
  const tabs = TABS.filter((t) => t.show());
  if (!tabs.length) {
    content.innerHTML = emptyHtml('Access denied', 'Your role has no administration permissions.');
  } else {
    content.innerHTML = `
      <div class="page-head"><div><h1>Administration</h1><p>Assign technicians, manage users and build roles.</p></div></div>
      <div class="admin-tabs" role="tablist">${tabs.map((t) =>
        `<button type="button" role="tab" data-tab="${t.key}" aria-selected="false">${esc(t.label)}</button>`).join('')}</div>
      <div id="tab-body"></div>`;
    $$('[data-tab]').forEach((b) => b.addEventListener('click', () => select(b.dataset.tab, tabs)));
    select(tabs[0].key, tabs);
  }
}

function select(key, tabs) {
  $$('[data-tab]').forEach((b) => b.setAttribute('aria-selected', String(b.dataset.tab === key)));
  $('#tab-body').innerHTML = `<section class="card" id="panel">${loadingHtml()}</section>`;
  tabs.find((t) => t.key === key).load();
}

const fail = (error) => { $('#panel').innerHTML = emptyHtml('Could not load this section', error.message); toast(error.message, 'error'); };

// ---------------- tickets (monitoring and assignment) ----------------
async function loadTickets() {
  try {
    const [tickets, technicians] = await Promise.all([get('/api/tickets'), get('/api/users/technicians')]);
    if (!tickets.length) { $('#panel').innerHTML = emptyHtml('No tickets registered yet'); return; }
    $('#panel').innerHTML = `
      <div class="table-wrap"><table class="stack">
        <thead><tr><th>#</th><th>Title</th><th>Priority</th><th>Status</th><th>Requester</th><th>Technician</th></tr></thead>
        <tbody>${tickets.map((t) => `<tr>
          <td data-label="#" class="id">${t.id}</td>
          <td data-label="Title"><a class="title" href="ticket-detail.html?id=${t.id}">${esc(t.title)}</a><small>${esc(categoryLabel(t.category))} - ${esc(formatDate(t.createdAt))}</small></td>
          <td data-label="Priority">${priorityBadge(t.priority)}</td>
          <td data-label="Status">${statusBadge(t.status)}</td>
          <td data-label="Requester">${esc(t.requester.fullName)}</td>
          <td data-label="Technician">${assignCell(t, technicians)}</td>
        </tr>`).join('')}</tbody></table></div>`;
    $$('[data-assign]').forEach((select) => select.addEventListener('change', async () => {
      if (!select.value) return;
      try {
        await patch(`/api/tickets/${select.dataset.assign}/assign`, { technicianId: Number(select.value) });
        toast('Technician assigned', 'success');
      } catch (error) {
        toast(error.message, 'error');
      }
      loadTickets();
    }));
  } catch (error) { fail(error); }
}

function assignCell(ticket, technicians) {
  if (!ticket.availableActions.includes('ASSIGN')) {
    return ticket.technician ? esc(ticket.technician.fullName) : '<span class="id">-</span>';
  }
  const current = ticket.technician?.id;
  return `<select data-assign="${ticket.id}" aria-label="Technician for ticket ${ticket.id}">
    <option value="">${current ? 'Reassign...' : 'Assign...'}</option>
    ${technicians.filter((t) => t.id !== current).map((t) => `<option value="${t.id}">${esc(t.fullName)}</option>`).join('')}
  </select>${current ? `<small>Now: ${esc(ticket.technician.fullName)}</small>` : ''}`;
}

// ---------------- technicians ----------------
async function loadTechnicians() {
  try {
    const [technicians, tickets] = await Promise.all([
      get('/api/users/technicians'), can('TICKET_READ_ALL') ? get('/api/tickets') : Promise.resolve([])]);
    if (!technicians.length) {
      $('#panel').innerHTML = emptyHtml('No technicians yet', 'Create a user with a role that can work on tickets (for example TECHNICIAN).');
      return;
    }
    const load = (id) => tickets.filter((t) => t.technician?.id === id && t.status !== 'CLOSED');
    $('#panel').innerHTML = `<div class="table-wrap"><table class="stack">
      <thead><tr><th>Name</th><th>Email</th><th>Active tickets</th><th>In progress</th></tr></thead>
      <tbody>${technicians.map((t) => `<tr>
        <td data-label="Name"><strong>${esc(t.fullName)}</strong></td>
        <td data-label="Email">${esc(t.email)}</td>
        <td data-label="Active tickets">${load(t.id).length}</td>
        <td data-label="In progress">${load(t.id).filter((x) => x.status === 'IN_PROGRESS').length}</td>
      </tr>`).join('')}</tbody></table></div>`;
  } catch (error) { fail(error); }
}

// ---------------- users ----------------
async function loadUsers() {
  try {
    const manage = can('USERS_MANAGE');
    const [users, roles] = await Promise.all([get('/api/users'), get('/api/roles')]);
    const me = getUser();
    $('#panel').innerHTML = `
      <div class="card-head"><h2>Registered users (${users.length})</h2>
        ${manage ? '<button class="btn primary" id="new-user" type="button">New user</button>' : ''}</div>
      <div class="table-wrap"><table class="stack">
        <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th>Created</th></tr></thead>
        <tbody>${users.map((u) => `<tr>
          <td data-label="Name"><strong>${esc(u.fullName)}</strong></td>
          <td data-label="Email">${esc(u.email)}</td>
          <td data-label="Role">${manage && u.id !== me.id
            ? `<select data-role="${u.id}" aria-label="Role of ${esc(u.fullName)}">${roles.map((r) =>
                `<option value="${esc(r.name)}" ${r.name === u.role ? 'selected' : ''}>${esc(r.name)}</option>`).join('')}</select>`
            : `<span class="badge plain">${esc(u.role)}</span>`}</td>
          <td data-label="Status">${manage && u.id !== me.id
            ? `<button class="btn sm" data-toggle="${u.id}" data-enabled="${u.enabled}" type="button">${u.enabled ? 'Disable' : 'Enable'}</button> `
            : ''}<span class="badge ${u.enabled ? 'RESOLVED' : 'CLOSED'}">${u.enabled ? 'Active' : 'Disabled'}</span></td>
          <td data-label="Created">${esc(formatDate(u.createdAt))}</td>
        </tr>`).join('')}</tbody></table></div>`;

    $('#new-user')?.addEventListener('click', () => userDialog(roles));
    $$('[data-role]').forEach((s) => s.addEventListener('change', async () => {
      try { await patch(`/api/users/${s.dataset.role}`, { role: s.value }); toast('Role updated', 'success'); }
      catch (error) { toast(error.message, 'error'); }
      loadUsers();
    }));
    $$('[data-toggle]').forEach((b) => b.addEventListener('click', async () => {
      const enable = b.dataset.enabled !== 'true';
      try { await patch(`/api/users/${b.dataset.toggle}`, { enabled: enable }); toast(enable ? 'User enabled' : 'User disabled', 'success'); }
      catch (error) { toast(error.message, 'error'); }
      loadUsers();
    }));
  } catch (error) { fail(error); }
}

function userDialog(roles) {
  const dialog = openDialog(`
    <h2>New user</h2>
    <form id="user-form" novalidate>
      ${fieldHtml({ name: 'fullName', label: 'Full name' })}
      ${fieldHtml({ name: 'email', label: 'Email', type: 'email' })}
      ${fieldHtml({ name: 'password', label: 'Temporary password', type: 'password', hint: PASSWORD_HINT, autocomplete: 'new-password' })}
      ${selectHtml({ name: 'role', label: 'Role', options: roles.map((r) => [r.name, `${r.name} - ${r.description}`]), value: 'TECHNICIAN' })}
      <div class="form-actions">
        <button class="btn" type="button" data-close>Cancel</button>
        <button class="btn primary" type="submit">Create user</button>
      </div>
    </form>`);
  dialog.querySelector('[data-close]').addEventListener('click', () => dialog.close());
  dialog.querySelector('#user-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.target;
    const body = { fullName: form.fullName.value.trim(), email: form.email.value.trim(), password: form.password.value, role: form.role.value };
    const errors = {};
    if (!body.fullName) errors.fullName = 'Full name is required';
    if (!body.email) errors.email = 'Email is required';
    if (!STRONG_PASSWORD.test(body.password)) errors.password = PASSWORD_HINT;
    setFormError(form, '');
    if (showFieldErrors(form, errors)) return;
    try {
      await withBusy($('button[type=submit]', form), () => post('/api/users', body));
      toast('User created', 'success');
      dialog.close();
      loadUsers();
    } catch (error) {
      if (!showFieldErrors(form, error.fieldErrors)) setFormError(form, error.message);
    }
  });
}

// ---------------- roles ----------------
async function loadRoles() {
  try {
    const [roles, catalog] = await Promise.all([get('/api/roles'), get('/api/roles/permissions')]);
    $('#panel').innerHTML = `
      <div class="card-head"><h2>Roles (${roles.length})</h2><button class="btn primary" id="new-role" type="button">New role</button></div>
      <div class="role-grid">${roles.map((r) => `
        <article class="role-card">
          <header><h3>${esc(r.name)}</h3><span class="badge plain ${r.systemRole ? 'CLOSED' : 'RESOLVED'}">${r.systemRole ? 'System' : 'Custom'}</span></header>
          <p>${esc(r.description)}</p>
          <div>${r.permissions.map((p) => `<span class="chip">${esc(p)}</span>`).join('')}</div>
          <small class="id">${r.userCount} user(s)</small>
          ${r.systemRole ? '' : `<div class="inline-actions">
            <button class="btn sm" data-edit="${esc(r.name)}" type="button">Edit</button>
            <button class="btn sm danger" data-delete="${esc(r.name)}" type="button">Delete</button></div>`}
        </article>`).join('')}</div>`;
    $('#new-role').addEventListener('click', () => roleDialog(catalog));
    $$('[data-edit]').forEach((b) => b.addEventListener('click', () => roleDialog(catalog, roles.find((r) => r.name === b.dataset.edit))));
    $$('[data-delete]').forEach((b) => b.addEventListener('click', async () => {
      if (!(await confirmDialog('Delete role?', `The role ${b.dataset.delete} will be removed.`, 'Delete', true))) return;
      try { await del(`/api/roles/${encodeURIComponent(b.dataset.delete)}`); toast('Role deleted', 'success'); }
      catch (error) { toast(error.message, 'error'); }
      loadRoles();
    }));
  } catch (error) { fail(error); }
}

function roleDialog(catalog, role = null) {
  const dialog = openDialog(`
    <h2>${role ? `Edit role ${esc(role.name)}` : 'New role'}</h2>
    <form id="role-form" novalidate>
      ${role ? '' : fieldHtml({ name: 'name', label: 'Name', hint: 'Upper case letters, digits and underscores. Example: SUPPORT_LEAD' })}
      ${fieldHtml({ name: 'description', label: 'Description', value: role?.description || '' })}
      <div class="field" id="perm-field">
        <label>Permissions</label>
        <div class="checks">${catalog.map((p) => `
          <label class="check"><input type="checkbox" name="permissions" value="${esc(p.name)}" ${role?.permissions.includes(p.name) ? 'checked' : ''}>
            <span><strong>${esc(p.name)}</strong><small>${esc(p.description)}</small></span></label>`).join('')}</div>
        <span class="field-error"></span>
      </div>
      <div class="form-actions">
        <button class="btn" type="button" data-close>Cancel</button>
        <button class="btn primary" type="submit">${role ? 'Save changes' : 'Create role'}</button>
      </div>
    </form>`);
  dialog.querySelector('[data-close]').addEventListener('click', () => dialog.close());
  dialog.querySelector('#role-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.target;
    const permissions = $$('input[name=permissions]:checked', form).map((i) => i.value);
    const body = { name: role ? role.name : form.name.value.trim().toUpperCase(), description: form.description.value.trim(), permissions };
    setFormError(form, '');
    const errors = {};
    if (!role && !/^[A-Z][A-Z0-9_]{1,49}$/.test(body.name)) errors.name = 'Use upper case letters, digits or underscores (2-50 characters)';
    if (!body.description) errors.description = 'Description is required';
    const hasErrors = showFieldErrors(form, errors);
    $('#perm-field', form).classList.toggle('invalid', !permissions.length);
    $('#perm-field .field-error', form).textContent = permissions.length ? '' : 'Select at least one permission';
    if (hasErrors || !permissions.length) return;
    try {
      await withBusy($('button[type=submit]', form), () => (role ? put(`/api/roles/${encodeURIComponent(role.name)}`, body) : post('/api/roles', body)));
      toast(role ? 'Role updated' : 'Role created', 'success');
      dialog.close();
      loadRoles();
    } catch (error) {
      if (!showFieldErrors(form, error.fieldErrors)) setFormError(form, error.message);
    }
  });
}
