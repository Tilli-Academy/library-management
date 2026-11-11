import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'

import { AuthProvider } from '../../auth/AuthContext'
import type { Book, Page, User } from '../../lib/types'
import { CatalogPage } from './CatalogPage'

const xssTitle = '<img src=x onerror="window.hacked=true"><script>window.hacked=true</script>'

function mockServer(user: User) {
  const books: Page<Book> = {
    items: [
      { id: 1, isbn: null, title: xssTitle, author: 'Mallory', genre: null, description: null, publishedYear: null, totalCopies: 2, availableCopies: 1 },
    ],
    page: 0,
    size: 12,
    totalItems: 1,
    totalPages: 1,
  }
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = String(input)
    const body = url.includes('/auth/me') ? user : books
    return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })
  })
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AuthProvider>
        <MemoryRouter>
          <CatalogPage />
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

const member: User = { id: 'u1', email: 'm@example.com', displayName: 'Mia Member', role: 'MEMBER', enabled: true, createdAt: '2026-01-01T00:00:00Z' }

describe('CatalogPage', () => {
  it('shows a malicious title as plain text and never runs it', async () => {
    mockServer(member)
    renderPage()
    expect(await screen.findByRole('heading', { name: xssTitle })).toBeInTheDocument()
    expect(document.querySelector('img[src="x"]')).toBeNull()
    expect(document.querySelector('script')).toBeNull()
    expect((window as unknown as { hacked?: boolean }).hacked).toBeUndefined()
  })

  it('hides librarian buttons from members', async () => {
    mockServer(member)
    renderPage()
    await screen.findByRole('heading', { name: xssTitle })
    expect(screen.queryByRole('button', { name: /add book/i })).toBeNull()
    expect(screen.queryByRole('button', { name: /lend/i })).toBeNull()
  })

  it('shows librarian buttons to librarians', async () => {
    mockServer({ ...member, role: 'LIBRARIAN' })
    renderPage()
    expect(await screen.findByRole('button', { name: /add book/i })).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: /lend/i })).toBeInTheDocument()
  })
})
