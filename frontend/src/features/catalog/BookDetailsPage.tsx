import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, Pencil } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router'

import { useAuth } from '../../auth/AuthContext'
import { useToast } from '../../components/Toast'
import { Badge, BookCover, Button, ErrorAlert, Spinner } from '../../components/ui'
import { api, coverUrl } from '../../lib/api'
import { expectedBackLabel, formatDate } from '../../lib/format'
import type { Book } from '../../lib/types'
import { BookFormModal } from './BookModals'

/** Everything about one book: cover, availability, about the book and about the author. */
export function BookDetailsPage() {
  const { id } = useParams()
  const bookId = Number(id)
  const { isLibrarian } = useAuth()
  const { toast, show } = useToast()
  const [editing, setEditing] = useState(false)

  const book = useQuery({
    queryKey: ['books', 'detail', bookId],
    queryFn: () => api.get<Book>(`/books/${bookId}`),
    enabled: Number.isInteger(bookId) && bookId > 0,
  })

  if (!Number.isInteger(bookId) || bookId <= 0) return <ErrorAlert error={new Error('This book does not exist.')} />
  if (book.isPending) return <Spinner />
  if (book.isError) return <ErrorAlert error={book.error} title="Couldn't load this book" />
  const b = book.data

  return (
    <>
      <Link to="/" className="mb-6 inline-flex items-center gap-1 text-sm text-ink-soft hover:text-ink">
        <ArrowLeft className="size-4" aria-hidden /> Back to the catalogue
      </Link>

      <article className="grid gap-8 md:grid-cols-[minmax(0,240px)_1fr]">
        <BookCover title={b.title} src={coverUrl(b)} eager className="aspect-[2/3] w-full max-w-60 justify-self-center md:justify-self-start" />

        <div className="min-w-0">
          <div className="mb-2">
            {b.status === 'COMING_SOON' ? (
              <Badge tone="violet">Coming soon{b.expectedArrival ? ` · expected ${formatDate(b.expectedArrival)}` : ''}</Badge>
            ) : b.availableCopies > 0 ? (
              <Badge tone="green">
                Available now · {b.availableCopies} of {b.totalCopies} on the shelf
              </Badge>
            ) : (
              <Badge tone="amber">{expectedBackLabel(b.expectedBackAt)}</Badge>
            )}
          </div>
          <h1 className="font-display text-3xl font-bold tracking-tight">{b.title}</h1>
          <p className="mt-1 text-lg text-ink-soft">by {b.author}</p>

          <dl className="mt-4 flex flex-wrap gap-x-6 gap-y-1 text-sm text-ink-soft">
            {b.genre && (
              <div>
                <dt className="inline text-ink-faint">Genre: </dt>
                <dd className="inline">{b.genre}</dd>
              </div>
            )}
            {b.publishedYear && (
              <div>
                <dt className="inline text-ink-faint">Published: </dt>
                <dd className="inline">{b.publishedYear}</dd>
              </div>
            )}
            {b.isbn && (
              <div>
                <dt className="inline text-ink-faint">ISBN: </dt>
                <dd className="inline">{b.isbn}</dd>
              </div>
            )}
          </dl>

          {isLibrarian && (
            <Button variant="secondary" className="mt-4" onClick={() => setEditing(true)}>
              <Pencil className="size-4" aria-hidden /> Edit book, cover and texts
            </Button>
          )}

          <section className="mt-8">
            <h2 className="font-display text-2xl font-bold">About the book</h2>
            {/* Plain text only: React shows it as text, line breaks kept, never as HTML. */}
            <p className="mt-2 leading-relaxed whitespace-pre-line text-ink-soft">
              {b.description || 'No description yet.'}
            </p>
          </section>

          <section className="mt-8">
            <h2 className="font-display text-2xl font-bold">About {b.author}</h2>
            <p className="mt-2 leading-relaxed whitespace-pre-line text-ink-soft">
              {b.aboutAuthor || 'No information about the author yet.'}
            </p>
          </section>
        </div>
      </article>

      {editing && (
        <BookFormModal
          book={b}
          onClose={() => setEditing(false)}
          onSaved={(message) => {
            setEditing(false)
            show(message)
          }}
        />
      )}
      {toast}
    </>
  )
}
