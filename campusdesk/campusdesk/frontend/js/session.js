// Session handling. The JWT lives in sessionStorage, so it disappears when the tab is closed.
const TOKEN_KEY = 'campusdesk.token';
const USER_KEY = 'campusdesk.user';

export function saveSession(auth) {
  sessionStorage.setItem(TOKEN_KEY, auth.token);
  sessionStorage.setItem(USER_KEY, JSON.stringify(auth.user));
}

export function updateUser(user) {
  sessionStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function getToken() {
  return sessionStorage.getItem(TOKEN_KEY);
}

export function getUser() {
  try {
    return JSON.parse(sessionStorage.getItem(USER_KEY));
  } catch {
    return null;
  }
}

export function clearSession() {
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(USER_KEY);
}

export function isLoggedIn() {
  return Boolean(getToken() && getUser());
}

/** UI hint only: the backend re-checks every permission on every request. */
export function can(permission) {
  return Boolean(getUser()?.permissions?.includes(permission));
}

export function canAny(...permissions) {
  return permissions.some(can);
}

export function logout() {
  clearSession();
  window.location.href = 'index.html';
}

/** Redirects to the login screen when there is no session. */
export function requireSession() {
  if (!isLoggedIn()) {
    window.location.replace('index.html');
    return false;
  }
  return true;
}
