import axios from 'axios';

export const http = axios.create({ baseURL: '/api', timeout: 15000 });
let session = null;
let refreshing = null;
let expired = () => {};
export function setSession(value) { session = value; }
export function onExpired(callback) { expired = callback; }
export function currentSession() { return session; }
export async function refreshSession() {
  if (!session) throw new Error('Please sign in.');
  if (!refreshing) {
    const original = session;
    refreshing = http.post('/auth/refresh', { refreshToken: original.refreshToken })
      .then(async ({ data }) => {
        if (session !== original) {
          await http.post('/auth/logout', { refreshToken: data.refreshToken }).catch(() => {});
          throw new Error('Session changed');
        }
        session = data;
        return data;
      }).catch(error => {
        if (session === original && error.response?.status === 401) { session = null; expired(); }
        throw error;
      }).finally(() => { refreshing = null; });
  }
  return refreshing;
}
http.interceptors.request.use(config => {
  if (session && !config.url.startsWith('/auth/')) config.headers.Authorization = `Bearer ${session.accessToken}`;
  return config;
});
http.interceptors.response.use(response => response, async error => {
  const config = error.config;
  if (error.response?.status === 401 && session && config && !config._retried && !config.url.startsWith('/auth/')) {
    config._retried = true;
    await refreshSession();
    return http(config);
  }
  throw error;
});
export async function signOut() {
  const old = session;
  session = null;
  if (old) await http.post('/auth/logout', { refreshToken: old.refreshToken }).catch(() => {});
}
export function errorText(error) { return error.response?.data?.message || error.message || 'Something went wrong.'; }