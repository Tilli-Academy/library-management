import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { api, ApiError, query, serverFieldError, setUnauthorizedHandler } from './api'

function jsonResponse(status: number, body: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function clearCookies() {
  for (const cookie of document.cookie.split('; ')) {
    const name = cookie.split('=')[0]
    if (name) document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`
  }
}

describe('api client', () => {
  beforeEach(clearCookies)
  afterEach(() => setUnauthorizedHandler(null))

  it('sends the CSRF token from the cookie on changes, and only to our own origin', async () => {
    document.cookie = 'XSRF-TOKEN=abc123; path=/'
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(201, { id: 1 }))

    await api.post('/admin/books', { title: 'X' })

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/admin/books')
    expect(init?.credentials).toBe('same-origin')
    expect((init!.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('abc123')
  })

  it('fetches a CSRF token first when there is none yet', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      if (String(input).endsWith('/auth/csrf')) {
        document.cookie = 'XSRF-TOKEN=fresh; path=/'
        return new Response(null, { status: 204 })
      }
      return jsonResponse(200, {})
    })

    await api.post('/auth/login', { email: 'a@b.c', password: 'x' })

    const [csrfUrl] = fetchMock.mock.calls[0]
    const [, loginInit] = fetchMock.mock.calls[1]
    expect(String(csrfUrl)).toBe('/api/v1/auth/csrf')
    expect((loginInit!.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('fresh')
  })

  it('does not send a CSRF header on reads', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(200, []))
    await api.get('/books')
    const [, init] = fetchMock.mock.calls[0]
    expect((init!.headers as Record<string, string>)['X-XSRF-TOKEN']).toBeUndefined()
  })

  it('turns problem-details responses into readable errors with field messages', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(400, { detail: 'Some fields are invalid.', errors: { title: 'Title is required.' } }),
    )
    const error = await api.get('/books').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).message).toBe('Some fields are invalid.')
    expect(serverFieldError(error, 'title')).toBe('Title is required.')
  })

  it('tells the app when the session has ended', async () => {
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(401, { detail: 'Please sign in to continue.' }))
    await expect(api.get('/me/loans')).rejects.toBeInstanceOf(ApiError)
    expect(onUnauthorized).toHaveBeenCalledOnce()
  })

  it('reports network failures without technical details', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'))
    await expect(api.get('/books')).rejects.toThrow('Cannot reach the server.')
  })

  it('URL-encodes query values so they cannot break the URL', () => {
    expect(query({ q: 'a&b=c #1', page: 0, available: undefined, empty: '' })).toBe('?q=a%26b%3Dc+%231&page=0')
  })
})
