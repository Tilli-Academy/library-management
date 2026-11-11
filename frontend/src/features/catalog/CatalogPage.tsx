import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { PackageCheck, Pencil, Plus, Search, Trash2, UserRoundPlus } from 'lucide-react'
import { useEffect, useState } from 'react'

import { useAuth } from '../../auth/AuthContext'
import { Badge, BookCover, Button, EmptyState, ErrorAlert, PageHeader, Pagination, Spinner } from '../../components/ui'
import { useToast } from '../../components/Toast'
import { api, query } from '../../lib/api'
import { expectedBackLabel, formatDate } from '../../lib/format'
import type { Book, Page } from '../../lib/types'
import { BookFormModal, IssueLoanModal } from './BookModals'

function useDebounced<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delay)
    return () => window.clearTimeout(timer)
  }, [value, delay])
  return debounced
}

type Filter = 'all' | 'available' | 'comingSoon'

const filters: { label: string; value: Filter }[] = [
  { label: 'All', value: 'all' },
  { label: 'Available', value: 'available' },
  { label: 'Coming soon', value: 'comingSoon' },
]

type Dialog = { kind: 'add' } | { kind: 'edit'; book: Book } | { kind: 'lend'; book: Book } | null

export function CatalogPage() {
  const { user, isLibrarian } = useAuth()
  const queryClient = useQueryClient()
  const { toast, show } = useToast()
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState<Filter>('all')
  const [dialog, setDialog] = useState<Dialog>(null)
  const q = useDebounced(search.trim())

  // The page number belongs to one search; a new search or filter starts again at page 1.
  const searchKey = `${q}|${filter}`
  const [paging, setPaging] = useState({ key: searchKey, page: 0 })
  const page = paging.key === searchKey ? paging.page : 0
  const setPage = (next: number) => setPaging({ key: searchKey, page: next })

  const books = useQuery({
    queryKey: ['books', { q, filter, page }],
    queryFn: () =>
      api.get<Page<Book>>(
        `/books${query({ q, available: filter === 'available' || undefined, comingSoon: filter === 'comingSoon' || undefined, page, size: 12 })}`,
      ),
    placeholderData: keepPreviousData,
  })

  const remove = useMutation({
    mutationFn: (book: Book) => api.delete(`/admin/books/${book.id}`),
    onSuccess: async (_, book) => {
      await queryClient.invalidateQueries({ queryKey: ['books'] })
      show(`Removed “${book.title}”.`)
    },
  })

  function closeWith(message: string) {
    setDialog(null)
    show(message)
  }

  function confirmDelete(book: Book) {
    if (window.confirm(`Delete “${book.title}” from the catalogue? This can't be undone.`)) remove.mutate(book)
  }

  return (
    <>
      <PageHeader
        title={`Hello, ${user?.displayName.split(' ')[0] ?? 'reader'}`}
        subtitle="Find your next book. Ask at the desk to borrow one."
        actions={
          isLibrarian && (
            <Button onClick={() => setDialog({ kind: 'add' })}>
              <Plus className="size-4" aria-hidden /> Add book
            </Button>
          )
        }
      />

      <div className="mb-6 flex flex-col gap-3 sm:flex-row">
        <label className="relative flex-1">
          <span className="sr-only">Search books</span>
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-faint" aria-hidden />
          <input
            type="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            maxLength={100}
            placeholder="Search title, author, genre or ISBN…"
            className="w-full rounded-xl border-0 bg-surface py-3 pr-3 pl-10 text-sm ring-1 ring-line placeholder:text-ink-faint focus:ring-2 focus:ring-brand-600 focus:outline-none"
          />
        </label>
        <div className="flex rounded-xl bg-surface p-1 ring-1 ring-line" role="group" aria-label="Filter">
          {filters.map((option) => (
            <button
              key={option.value}
              type="button"
              aria-pressed={filter === option.value}
              onClick={() => setFilter(option.value)}
              className={`flex-1 rounded-lg px-3 py-2 text-sm font-medium whitespace-nowrap ${
                filter === option.value ? 'bg-brand-700 text-white' : 'text-ink-soft hover:bg-subtle'
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>

      <div className="mb-4">
        <ErrorAlert error={remove.error} />
      </div>

      {books.isPending ? (
        <Spinner label="Loading the catalogue…" />
      ) : books.isError ? (
        <ErrorAlert error={books.error} title="Couldn't load books" />
      ) : books.data.items.length === 0 ? (
        <EmptyState title={q || filter !== 'all' ? 'No books match' : 'The catalogue is empty'}>
          {q || filter !== 'all'
            ? 'Try a different search or show all books.'
            : isLibrarian
              ? 'Add the first book to get started.'
              : 'Check back soon — the librarians are stocking the shelves.'}
        </EmptyState>
      ) : (
        <>
          <p className="mb-3 text-sm text-ink-faint" aria-live="polite">
            {books.data.totalItems} {books.data.totalItems === 1 ? 'book' : 'books'}
          </p>
          <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {books.data.items.map((book) => (
              <li key={book.id} className="flex gap-4 rounded-2xl bg-surface p-4 shadow-sm ring-1 ring-line">
                <BookCover title={book.title} className="h-32 w-22 shrink-0" />
                <div className="flex min-w-0 flex-1 flex-col">
                  <div className="mb-1">
                    {book.status === 'COMING_SOON' ? (
                      <Badge tone="violet">
                        Coming soon{book.expectedArrival ? ` · ${formatDate(book.expectedArrival)}` : ''}
                      </Badge>
                    ) : book.availableCopies > 0 ? (
                      <Badge tone="green">
                        {book.availableCopies} of {book.totalCopies} available
                      </Badge>
                    ) : (
                      <Badge tone="amber">{expectedBackLabel(book.expectedBackAt)}</Badge>
                    )}
                  </div>
                  <h2 className="line-clamp-2 font-display font-bold text-ink" title={book.title}>
                    {book.title}
                  </h2>
                  <p className="truncate text-sm text-ink-soft">{book.author}</p>
                  <p className="mt-0.5 truncate text-xs text-ink-faint">
                    {[book.genre, book.publishedYear].filter(Boolean).join(' · ')}
                  </p>
                  {isLibrarian && (
                    <div className="mt-auto flex flex-wrap gap-1 pt-3">
                      {book.status === 'COMING_SOON' ? (
                        <Button variant="secondary" className="min-h-8 px-2.5 py-1 text-xs" onClick={() => setDialog({ kind: 'edit', book })}>
                          <PackageCheck className="size-3.5" aria-hidden /> Copies arrived
                        </Button>
                      ) : (
                        <Button
                          variant="secondary"
                          className="min-h-8 px-2.5 py-1 text-xs"
                          disabled={book.availableCopies === 0}
                          onClick={() => setDialog({ kind: 'lend', book })}
                        >
                          <UserRoundPlus className="size-3.5" aria-hidden /> Lend
                        </Button>
                      )}
                      <Button variant="ghost" className="min-h-8 px-2 py-1" onClick={() => setDialog({ kind: 'edit', book })} aria-label={`Edit ${book.title}`}>
                        <Pencil className="size-3.5" aria-hidden />
                      </Button>
                      <Button variant="ghost" className="min-h-8 px-2 py-1" onClick={() => confirmDelete(book)} aria-label={`Delete ${book.title}`}>
                        <Trash2 className="size-3.5" aria-hidden />
                      </Button>
                    </div>
                  )}
                </div>
              </li>
            ))}
          </ul>
          <Pagination page={books.data.page} totalPages={books.data.totalPages} onChange={setPage} />
        </>
      )}

      {dialog?.kind === 'add' && <BookFormModal onClose={() => setDialog(null)} onSaved={closeWith} />}
      {dialog?.kind === 'edit' && (
        <BookFormModal book={dialog.book} onClose={() => setDialog(null)} onSaved={closeWith} />
      )}
      {dialog?.kind === 'lend' && (
        <IssueLoanModal book={dialog.book} onClose={() => setDialog(null)} onIssued={closeWith} />
      )}
      {toast}
    </>
  )
}
