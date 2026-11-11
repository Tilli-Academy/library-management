/** Only allow redirects to paths inside this app (never to another website). */
export function safeRedirect(path: unknown): string {
  return typeof path === 'string' && /^\/(?![/\\])/.test(path) && !path.includes('\\') ? path : '/'
}
