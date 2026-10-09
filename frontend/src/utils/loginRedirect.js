/**
 * Return-to path carried through the login page (`/login?redirect=...`), so a guest who opens an app
 * link (e.g. a Tip of the Day on Home) lands on that page after signing in instead of the default one.
 *
 * Only in-app `/app` paths are accepted: the value comes from the URL, so anything else (absolute or
 * protocol-relative URLs, other routes) is dropped to avoid an open redirect.
 */
const APP_PATH_PATTERN = /^\/app(?:[/?#]|$)/

export const getSafeLoginRedirect = (value) => {
  const path = Array.isArray(value) ? value[0] : value
  if (typeof path !== 'string' || path.includes('\\')) {
    return null
  }
  return APP_PATH_PATTERN.test(path) ? path : null
}

export const buildLoginLocation = (to) => {
  const redirect = getSafeLoginRedirect(to?.fullPath)
  return redirect ? { path: '/login', query: { redirect } } : '/login'
}

// String form for full-page redirects outside the router (e.g. the API client on an expired session).
export const buildLoginUrl = (path) => {
  const redirect = getSafeLoginRedirect(path)
  return redirect ? `/login?${new URLSearchParams({ redirect })}` : '/login'
}
