const dateFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' })

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—'
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? '—' : dateFormat.format(date)
}

/** For a member looking at a book with no copies left: when can they get it? */
export function expectedBackLabel(expectedIso: string | null, now: Date = new Date()): string {
  if (!expectedIso) return 'All copies on loan'
  const due = new Date(expectedIso)
  if (due.getTime() < now.getTime()) return 'Expected back any day now'
  const day = 24 * 60 * 60 * 1000
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const startOfDue = new Date(due.getFullYear(), due.getMonth(), due.getDate()).getTime()
  const days = Math.round((startOfDue - startOfToday) / day)
  if (days <= 0) return 'Expected back today'
  if (days === 1) return 'Expected back tomorrow'
  return `Expected back in ${days} days (${formatDate(expectedIso)})`
}

/** "Due in 3 days", "Due today", "2 days overdue". */
export function dueLabel(dueIso: string, now: Date = new Date()): string {
  const day = 24 * 60 * 60 * 1000
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const due = new Date(dueIso)
  const startOfDue = new Date(due.getFullYear(), due.getMonth(), due.getDate()).getTime()
  const days = Math.round((startOfDue - startOfToday) / day)
  if (due.getTime() < now.getTime()) {
    const late = Math.max(1, -days)
    return `${late} day${late === 1 ? '' : 's'} overdue`
  }
  if (days === 0) return 'Due today'
  if (days === 1) return 'Due tomorrow'
  return `Due in ${days} days`
}
