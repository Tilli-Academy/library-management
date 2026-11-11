import { useMutation, useQueryClient } from '@tanstack/react-query'
import { PhoneCall } from 'lucide-react'

import { Button } from '../../components/ui'
import { api } from '../../lib/api'
import { formatDate } from '../../lib/format'
import type { Loan } from '../../lib/types'

/** Only digits and a leading + go into the tel: link (never anything that could change the link). */
function telHref(phone: string): string {
  return `tel:${phone.replace(/[^0-9+]/g, '')}`
}

/**
 * For a borrowed book: the member's phone with a Call button, and "Mark as called" so other librarians
 * can see someone already followed up.
 */
export function ContactMember({ loan, onDone }: { loan: Loan; onDone?: (message: string) => void }) {
  const queryClient = useQueryClient()
  const remind = useMutation({
    mutationFn: () => api.post<Loan>(`/admin/loans/${loan.id}/reminded`),
    onSuccess: async (updated) => {
      await queryClient.invalidateQueries({ queryKey: ['admin'] })
      onDone?.(`Noted: you called ${updated.memberName}.`)
    },
  })

  return (
    <div className="flex flex-wrap items-center gap-2">
      {loan.memberPhone ? (
        <a
          href={telHref(loan.memberPhone)}
          className="inline-flex min-h-10 items-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-emerald-500"
        >
          <PhoneCall className="size-4" aria-hidden /> Call {loan.memberPhone}
        </a>
      ) : (
        <span className="text-sm text-ink-faint">No phone number — email {loan.memberEmail}</span>
      )}
      <Button variant="secondary" onClick={() => remind.mutate()} loading={remind.isPending}>
        Mark as called
      </Button>
      {loan.lastRemindedAt && <span className="text-xs text-ink-faint">Last called {formatDate(loan.lastRemindedAt)}</span>}
      {remind.isError && (
        <span role="alert" className="text-xs text-red-700 dark:text-red-400">
          {remind.error.message}
        </span>
      )}
    </div>
  )
}
