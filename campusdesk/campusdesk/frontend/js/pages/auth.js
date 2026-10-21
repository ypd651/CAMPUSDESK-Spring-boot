import { post } from '../api.js';
import { isLoggedIn, saveSession } from '../session.js';
import { $, setFormError, showFieldErrors, withBusy } from '../ui.js';
import { LOGO } from '../layout.js';

if (isLoggedIn()) window.location.replace('dashboard.html');

$('#brand-logo').innerHTML = `${LOGO}<span>CampusDesk</span>`;
if (new URLSearchParams(window.location.search).has('expired')) $('#expired-banner').hidden = false;

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
