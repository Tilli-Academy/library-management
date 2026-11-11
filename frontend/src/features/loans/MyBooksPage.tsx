import { zodResolver } from '@hookform/resolvers/zod'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { BookMarked } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'

import { useAuth } from '../../auth/AuthContext'
import { Badge, BookCover, Button, EmptyState, ErrorAlert, PageHeader, Pagination, Spinner, TextField } from '../../components/ui'
import { api, query, serverFieldError } from '../../lib/api'
import { dueLabel, formatDate } from '../../lib/format'
import { profileSchema, type ProfileForm } from '../../lib/schemas'
import type { Loan, Page } from '../../lib/types'

export function MyBooksPage() {
  const [tab, setTab] = useState<'current' | 'history'>('current')
  const [page, setPage] = useState(0)
  const returned = tab === 'history'

  const loans = useQuery({
    queryKey: ['my-loans', { returned, page }],
    queryFn: () => api.get<Page<Loan>>(`/me/loans${query({ returned, page, size: 20 })}`),
    placeholderData: keepPreviousData,
  })

  return (
    <>
      <PageHeader title="My books" subtitle="What you're reading now, and everything you've borrowed before." />
      <PhoneCard />
      <div className="mb-6 inline-flex rounded-xl bg-surface p-1 ring-1 ring-line" role="tablist">
        {(['current', 'history'] as const).map((value) => (
          <button
            key={value}
            type="button"
            role="tab"
            aria-selected={tab === value}
            onClick={() => {
              setTab(value)
              setPage(0)
            }}
            className={`rounded-lg px-4 py-2 text-sm font-medium ${tab === value ? 'bg-brand-700 text-white' : 'text-ink-soft hover:bg-subtle'}`}
          >
            {value === 'current' ? 'Borrowed now' : 'History'}
          </button>
        ))}
      </div>

      {loans.isPending ? (
        <Spinner />
      ) : loans.isError ? (
        <ErrorAlert error={loans.error} title="Couldn't load your books" />
      ) : loans.data.items.length === 0 ? (
        <EmptyState icon={<BookMarked className="size-6" aria-hidden />} title={returned ? 'No history yet' : 'No books borrowed'}>
          {returned ? 'Books you return will be listed here.' : 'Find something in the catalogue and ask at the desk.'}
        </EmptyState>
      ) : (
        <>
          <ul className="space-y-3">
            {loans.data.items.map((loan) => (
              <li key={loan.id} className="flex items-center gap-4 rounded-2xl bg-surface p-4 shadow-sm ring-1 ring-line">
                <BookCover title={loan.bookTitle} className="h-20 w-14 shrink-0" />
                <div className="min-w-0 flex-1">
                  <h2 className="truncate font-display font-bold">{loan.bookTitle}</h2>
                  <p className="truncate text-sm text-ink-soft">{loan.bookAuthor}</p>
                  <p className="mt-1 text-xs text-ink-faint">
                    Borrowed {formatDate(loan.issuedAt)}
                    {loan.returnedAt ? ` · Returned ${formatDate(loan.returnedAt)}` : ` · Due ${formatDate(loan.dueAt)}`}
                  </p>
                </div>
                {!loan.returnedAt && <Badge tone={loan.overdue ? 'red' : 'violet'}>{dueLabel(loan.dueAt)}</Badge>}
              </li>
            ))}
          </ul>
          <Pagination page={loans.data.page} totalPages={loans.data.totalPages} onChange={setPage} />
        </>
      )}
    </>
  )
}

/** The member's phone number, which the library uses to remind them about overdue books. */
function PhoneCard() {
  const { user, updatePhone } = useAuth()
  const [editing, setEditing] = useState(!user?.phone)
  const [error, setError] = useState<unknown>(null)
  const { register, handleSubmit, formState } = useForm<ProfileForm>({
    resolver: zodResolver(profileSchema),
    defaultValues: { phone: user?.phone ?? '' },
  })

  const onSubmit = handleSubmit(async (form) => {
    setError(null)
    try {
      await updatePhone(form.phone)
      setEditing(false)
    } catch (e) {
      setError(e)
    }
  })

  if (!editing) {
    return (
      <div className="mb-6 flex flex-wrap items-center justify-between gap-2 rounded-2xl bg-surface p-4 text-sm ring-1 ring-line">
        <span className="text-ink-soft">
          The library will call <strong className="text-ink">{user?.phone}</strong> if a book is overdue.
        </span>
        <Button variant="ghost" onClick={() => setEditing(true)}>
          Change number
        </Button>
      </div>
    )
  }
  return (
    <form onSubmit={onSubmit} noValidate className="mb-6 space-y-3 rounded-2xl bg-surface p-4 ring-1 ring-line">
      {!user?.phone && (
        <p className="text-sm font-medium text-amber-800 dark:text-amber-300">
          Please add your phone number, so the library can reach you about your books.
        </p>
      )}
      <ErrorAlert error={error} />
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start">
        <div className="flex-1">
          <TextField
            label="Phone number"
            type="tel"
            autoComplete="tel"
            {...register('phone')}
            error={formState.errors.phone?.message ?? serverFieldError(error, 'phone')}
          />
        </div>
        <div className="flex gap-2 sm:pt-6">
          <Button type="submit" loading={formState.isSubmitting}>
            Save
          </Button>
          {user?.phone && (
            <Button variant="secondary" onClick={() => setEditing(false)}>
              Cancel
            </Button>
          )}
        </div>
      </div>
    </form>
  )
}
