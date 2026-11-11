import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, BookCopy, BookOpen, Library, Repeat, Users } from 'lucide-react'
import type { ComponentType } from 'react'
import { Link } from 'react-router'

import { ErrorAlert, PageHeader, Spinner } from '../../components/ui'
import { api } from '../../lib/api'
import { ContactMember } from './ContactMember'
import { dueLabel } from '../../lib/format'
import type { LibraryStats, Loan, Page } from '../../lib/types'

export function DashboardPage() {
  const stats = useQuery({ queryKey: ['admin', 'stats'], queryFn: () => api.get<LibraryStats>('/admin/stats') })
  const overdue = useQuery({
    queryKey: ['admin', 'loans', { status: 'OVERDUE', page: 0 }],
    queryFn: () => api.get<Page<Loan>>('/admin/loans?status=OVERDUE&size=5'),
  })

  return (
    <>
      <PageHeader title="Dashboard" subtitle="How the library is doing today." />
      {stats.isPending ? (
        <Spinner />
      ) : stats.isError ? (
        <ErrorAlert error={stats.error} title="Couldn't load statistics" />
      ) : (
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-3">
          <Stat icon={Library} label="Titles" value={stats.data.titles} />
          <Stat icon={BookCopy} label="Copies owned" value={stats.data.totalCopies} />
          <Stat icon={BookOpen} label="On the shelf" value={stats.data.availableCopies} />
          <Stat icon={Repeat} label="On loan" value={stats.data.activeLoans} />
          <Stat icon={AlertTriangle} label="Overdue" value={stats.data.overdueLoans} alert={stats.data.overdueLoans > 0} />
          <Stat icon={Users} label="Members" value={stats.data.members} />
        </div>
      )}

      <section className="mt-10">
        <div className="mb-3 flex items-baseline justify-between">
          <h2 className="font-display text-2xl font-bold">Overdue books — call these members</h2>
          <Link to="/admin/loans" className="text-sm font-semibold text-brand-700 hover:underline dark:text-brand-300">
            All loans →
          </Link>
        </div>
        {overdue.isPending ? (
          <Spinner />
        ) : overdue.isError ? (
          <ErrorAlert error={overdue.error} />
        ) : overdue.data.items.length === 0 ? (
          <p className="rounded-2xl bg-surface p-6 text-sm text-ink-soft ring-1 ring-line">Nothing overdue — every book is on time.</p>
        ) : (
          <ul className="divide-y divide-line rounded-2xl bg-surface ring-1 ring-line">
            {overdue.data.items.map((loan) => (
              <li key={loan.id} className="flex flex-col gap-3 p-4">
                <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
                  <div className="min-w-0">
                    <p className="truncate font-semibold">{loan.bookTitle}</p>
                    <p className="truncate text-sm text-ink-soft">
                      {loan.memberName} · {loan.memberEmail}
                    </p>
                  </div>
                  <span className="text-sm font-semibold text-red-700 dark:text-red-400">{dueLabel(loan.dueAt)}</span>
                </div>
                <ContactMember loan={loan} />
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  )
}

function Stat({ icon: Icon, label, value, alert = false }: { icon: ComponentType<{ className?: string }>; label: string; value: number; alert?: boolean }) {
  return (
    <div className="rounded-2xl bg-surface p-4 shadow-sm ring-1 ring-line sm:p-5">
      <div className={`mb-3 grid size-9 place-items-center rounded-lg ${alert ? 'bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-300' : 'bg-brand-50 text-brand-700 dark:bg-brand-950 dark:text-brand-300'}`}>
        <Icon className="size-5" aria-hidden />
      </div>
      <p className="text-sm text-ink-soft">{label}</p>
      <p className="text-2xl font-bold tabular-nums">{value.toLocaleString()}</p>
    </div>
  )
}
