import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Repeat, Undo2 } from 'lucide-react'
import { useState } from 'react'

import { useToast } from '../../components/Toast'
import { ContactMember } from './ContactMember'
import { Badge, Button, EmptyState, ErrorAlert, PageHeader, Pagination, Spinner } from '../../components/ui'
import { api, query } from '../../lib/api'
import { dueLabel, formatDate } from '../../lib/format'
import type { Loan, LoanStatus, Page } from '../../lib/types'

const tabs: { value: LoanStatus; label: string }[] = [
  { value: 'ACTIVE', label: 'On loan' },
  { value: 'OVERDUE', label: 'Overdue' },
  { value: 'RETURNED', label: 'Returned' },
]

export function LoansPage() {
  const queryClient = useQueryClient()
  const { toast, show } = useToast()
  const [status, setStatus] = useState<LoanStatus>('ACTIVE')
  const [page, setPage] = useState(0)

  const loans = useQuery({
    queryKey: ['admin', 'loans', { status, page }],
    queryFn: () => api.get<Page<Loan>>(`/admin/loans${query({ status, page, size: 20 })}`),
    placeholderData: keepPreviousData,
  })

  const giveBack = useMutation({
    mutationFn: (loan: Loan) => api.post<Loan>(`/admin/loans/${loan.id}/return`),
    onSuccess: async (loan) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['admin'] }),
        queryClient.invalidateQueries({ queryKey: ['books'] }),
      ])
      show(`“${loan.bookTitle}” returned by ${loan.memberName}.`)
    },
  })

  return (
    <>
      <PageHeader title="Loans" subtitle="Who borrowed what, who returned it, and who is late. To lend a book, use Lend in the catalogue." />
      <div className="mb-6 inline-flex rounded-xl bg-surface p-1 ring-1 ring-line" role="tablist">
        {tabs.map((tab) => (
          <button
            key={tab.value}
            type="button"
            role="tab"
            aria-selected={status === tab.value}
            onClick={() => {
              setStatus(tab.value)
              setPage(0)
            }}
            className={`rounded-lg px-4 py-2 text-sm font-medium ${status === tab.value ? 'bg-brand-700 text-white' : 'text-ink-soft hover:bg-subtle'}`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="mb-4">
        <ErrorAlert error={giveBack.error} />
      </div>

      {loans.isPending ? (
        <Spinner />
      ) : loans.isError ? (
        <ErrorAlert error={loans.error} title="Couldn't load loans" />
      ) : loans.data.items.length === 0 ? (
        <EmptyState icon={<Repeat className="size-6" aria-hidden />} title="Nothing here">
          {status === 'OVERDUE' ? 'No overdue books right now.' : 'No loans in this list yet.'}
        </EmptyState>
      ) : (
        <>
          <ul className="divide-y divide-line rounded-2xl bg-surface shadow-sm ring-1 ring-line">
            {loans.data.items.map((loan) => (
              <li key={loan.id} className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
                <div className="min-w-0 flex-1">
                  <p className="truncate font-semibold">{loan.bookTitle}</p>
                  <p className="truncate text-sm text-ink-soft">
                    {loan.memberName} · {loan.memberEmail}
                    {loan.memberPhone && ` · ${loan.memberPhone}`}
                  </p>
                  <p className="mt-0.5 text-xs text-ink-faint">
                    Lent {formatDate(loan.issuedAt)}
                    {loan.returnedAt ? ` · Returned ${formatDate(loan.returnedAt)}` : ` · Due ${formatDate(loan.dueAt)}`}
                  </p>
                  {loan.overdue && (
                    <div className="mt-2">
                      <ContactMember loan={loan} onDone={show} />
                    </div>
                  )}
                </div>
                <div className="flex items-center gap-3">
                  {!loan.returnedAt && <Badge tone={loan.overdue ? 'red' : 'violet'}>{dueLabel(loan.dueAt)}</Badge>}
                  {!loan.returnedAt && (
                    <Button
                      variant="secondary"
                      onClick={() => giveBack.mutate(loan)}
                      loading={giveBack.isPending && giveBack.variables?.id === loan.id}
                    >
                      <Undo2 className="size-4" aria-hidden /> Mark returned
                    </Button>
                  )}
                </div>
              </li>
            ))}
          </ul>
          <Pagination page={loans.data.page} totalPages={loans.data.totalPages} onChange={setPage} />
        </>
      )}
      {toast}
    </>
  )
}
