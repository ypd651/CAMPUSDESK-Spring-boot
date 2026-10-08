import { post } from '../api.js';
import { isLoggedIn, saveSession } from '../session.js';
import { $, PASSWORD_HINT, STRONG_PASSWORD, setFormError, showFieldErrors, toast, withBusy } from '../ui.js';
import { LOGO } from '../layout.js';

if (isLoggedIn()) window.location.replace('dashboard.html');

$('#brand-logo').innerHTML = `${LOGO}<span>CampusDesk</span>`;
if (new URLSearchParams(window.location.search).has('expired')) $('#expired-banner').hidden = false;

// ---- tabs ----
function selectTab(name) {
  for (const key of ['login', 'register']) {
    const active = key === name;
    $(`#tab-${key}`).setAttribute('aria-selected', String(active));
    $(`#panel-${key}`).hidden = !active;
  }
}
$('#tab-login').addEventListener('click', () => selectTab('login'));
$('#tab-register').addEventListener('click', () => selectTab('register'));

const isEmail = (value) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);

// ---- login ----
$('#login-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.target;
  const email = form.email.value.trim();
  const password = form.password.value;

  const errors = {};
  if (!email) errors.email = 'Email is required';
  else if (!isEmail(email)) errors.email = 'Email format is invalid';
  if (!password) errors.password = 'Password is required';
  setFormError(form, '');
  if (showFieldErrors(form, errors)) return;

  try {
    const auth = await withBusy($('button[type=submit]', form), () => post('/api/auth/login', { email, password }, { auth: false }));
    saveSession(auth);
    window.location.replace('dashboard.html');
  } catch (error) {
    if (!showFieldErrors(form, error.fieldErrors)) setFormError(form, error.message);
  }
});

// ---- register ----
$('#register-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.target;
  const fullName = form.fullName.value.trim();
  const email = form.email.value.trim();
  const password = form.password.value;

  const errors = {};
  if (!fullName) errors.fullName = 'Full name is required';
  if (!email) errors.email = 'Email is required';
  else if (!isEmail(email)) errors.email = 'Email format is invalid';
  if (!STRONG_PASSWORD.test(password)) errors.password = `Password is too weak. ${PASSWORD_HINT}`;
  if (form.confirm.value !== password) errors.confirm = 'Passwords do not match';
  setFormError(form, '');
  if (showFieldErrors(form, errors)) return;

  try {
    await withBusy($('button[type=submit]', form), () => post('/api/auth/register', { fullName, email, password }, { auth: false }));
    toast('Account created. You can sign in now.', 'success');
    form.reset();
    selectTab('login');
    $('#login-email').value = email;
    $('#login-password').focus();
  } catch (error) {
    if (!showFieldErrors(form, error.fieldErrors)) setFormError(form, error.message);
  }
});
