/**
 * HTTP client for the PageTurn API.
 *
 * Security notes:
 * - The session lives in an HttpOnly cookie that JavaScript can't read, so this file never sees or stores
 *   a token (nothing in localStorage that a malicious script could steal).
 * - Every request that changes data sends the CSRF token (from the XSRF-TOKEN cookie) in the X-XSRF-TOKEN
 *   header. Another website can't read that cookie, so it can't forge requests on the user's behalf.
 * - Requests always go to our own origin (relative URL); `credentials: 'same-origin'` never sends cookies
 *   anywhere else.
 */
const API_BASE = `${import.meta.env.VITE_API_BASE_URL ?? import.meta.env.BASE_URL.replace(/\/$/, '')}/api/v1`

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'

export class ApiError extends Error {
  readonly status: number
  /** Field name → message from server-side validation, e.g. { email: 'Enter a valid email address.' }. */
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

/** The server's validation message for one form field, if any. */
export function serverFieldError(error: unknown, field: string): string | undefined {
  return error instanceof ApiError ? error.fieldErrors[field] : undefined
}

let unauthorizedHandler: (() => void) | null = null

/** Called when the server says the session is gone (expired, signed out elsewhere, or account blocked). */
export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler
}

export function readCookie(name: string): string | null {
  const match = document.cookie.split('; ').find((part) => part.startsWith(`${name}=`))
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null
}

let csrfRequest: Promise<void> | null = null

/** Makes sure the CSRF cookie exists (asks the server for one the first time). */
async function ensureCsrfToken(): Promise<string> {
  let token = readCookie(CSRF_COOKIE)
  if (!token) {
    csrfRequest ??= fetch(`${API_BASE}/auth/csrf`, { credentials: 'same-origin' })
      .then(() => undefined)
      .finally(() => {
        csrfRequest = null
      })
    await csrfRequest
    token = readCookie(CSRF_COOKIE)
  }
  if (!token) throw new ApiError(0, 'Could not start a secure session. Please refresh the page.')
  return token
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  const isForm = body instanceof FormData
  // FormData (file upload): the browser sets the multipart Content-Type itself.
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json'
  if (method !== 'GET') headers[CSRF_HEADER] = await ensureCsrfToken()

  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      credentials: 'same-origin',
      body: body === undefined ? undefined : isForm ? body : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check your connection and try again.')
  }

  const payload = await readJson(response)
  if (response.ok) return payload as T
  if (response.status === 401 && !path.startsWith('/auth/')) unauthorizedHandler?.()
  throw toApiError(response, payload)
}

async function readJson(response: Response): Promise<unknown> {
  if (response.status === 204) return undefined
  if (!(response.headers.get('Content-Type') ?? '').includes('json')) return undefined
  try {
    return await response.json()
  } catch {
    return undefined
  }
}

function toApiError(response: Response, payload: unknown): ApiError {
  const body = isRecord(payload) ? payload : {}
  const message = typeof body.detail === 'string' && body.detail ? body.detail : defaultMessage(response.status)
  const fieldErrors: Record<string, string> = {}
  if (isRecord(body.errors)) {
    for (const [field, value] of Object.entries(body.errors)) {
      if (typeof value === 'string') fieldErrors[field] = value
    }
  }
  return new ApiError(response.status, message, fieldErrors)
}

function defaultMessage(status: number): string {
  if (status === 401) return 'Please sign in again.'
  if (status === 403) return 'You do not have permission to do this.'
  if (status === 404) return 'Not found.'
  if (status === 429) return 'Too many requests. Please wait a minute and try again.'
  if (status >= 500) return 'The server had a problem. Please try again.'
  return `Request failed (HTTP ${status}).`
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/** Builds a query string, skipping empty values. Values are URL-encoded. */
export function query(params: Record<string, string | number | boolean | undefined | null>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}

/** Address of a book's cover picture (null if it has none). The version makes browsers fetch new covers. */
export function coverUrl(book: { id: number; coverVersion: number | null }): string | null {
  return book.coverVersion == null ? null : `${API_BASE}/books/${book.id}/cover?v=${book.coverVersion}`
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body: unknown) => request<T>('PUT', path, body),
  patch: <T>(path: string, body: unknown) => request<T>('PATCH', path, body),
  /** Upload a file (multipart form). */
  putForm: <T>(path: string, form: FormData) => request<T>('PUT', path, form),
  delete: (path: string) => request<void>('DELETE', path),
}
