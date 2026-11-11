import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Search, Users } from 'lucide-react'
import { useEffect, useState } from 'react'

import { useAuth } from '../../auth/AuthContext'
import { useToast } from '../../components/Toast'
import { Badge, Button, EmptyState, ErrorAlert, PageHeader, Pagination, Spinner } from '../../components/ui'
import { api, query } from '../../lib/api'
import { formatDate } from '../../lib/format'
import type { Page, Role, User } from '../../lib/types'

export function UsersPage() {
  const { user: me } = useAuth()
  const queryClient = useQueryClient()
  const { toast, show } = useToast()
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setQ(search.trim())
      setPage(0)
    }, 300)
    return () => window.clearTimeout(timer)
  }, [search])

  const users = useQuery({
    queryKey: ['admin', 'users', { q, page }],
    queryFn: () => api.get<Page<User>>(`/admin/users${query({ q, page, size: 20 })}`),
    placeholderData: keepPreviousData,
  })

  const update = useMutation({
    mutationFn: ({ user, change }: { user: User; change: { role?: Role; enabled?: boolean } }) =>
      api.patch<User>(`/admin/users/${user.id}`, change),
    onSuccess: async (updated) => {
      await queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
      show(`Updated ${updated.displayName}.`)
    },
  })

  function changeRole(user: User, role: Role) {
    const message =
      role === 'LIBRARIAN'
        ? `Make ${user.displayName} a librarian? They will be able to manage books, loans and members.`
        : `Remove librarian access from ${user.displayName}?`
    if (window.confirm(message)) update.mutate({ user, change: { role } })
  }

  function toggleBlocked(user: User) {
    const message = user.enabled
      ? `Block ${user.displayName}? They will be signed out and can't sign in until unblocked.`
      : `Unblock ${user.displayName}?`
    if (window.confirm(message)) update.mutate({ user, change: { enabled: !user.enabled } })
  }

  return (
    <>
      <PageHeader title="Members" subtitle="Everyone with an account. Promote librarians or block accounts." />
      <label className="relative mb-6 block">
        <span className="sr-only">Search members</span>
        <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-faint" aria-hidden />
        <input
          type="search"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          maxLength={100}
          placeholder="Search by name or email…"
          className="w-full rounded-xl border-0 bg-surface py-3 pr-3 pl-10 text-sm ring-1 ring-line placeholder:text-ink-faint focus:ring-2 focus:ring-brand-600 focus:outline-none"
        />
      </label>

      <div className="mb-4">
        <ErrorAlert error={update.error} />
      </div>

      {users.isPending ? (
        <Spinner />
      ) : users.isError ? (
        <ErrorAlert error={users.error} title="Couldn't load members" />
      ) : users.data.items.length === 0 ? (
        <EmptyState icon={<Users className="size-6" aria-hidden />} title="No one found" />
      ) : (
        <>
          <ul className="divide-y divide-line rounded-2xl bg-surface shadow-sm ring-1 ring-line">
            {users.data.items.map((user) => {
              const isMe = user.id === me?.id
              return (
                <li key={user.id} className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="truncate font-semibold">{user.displayName}</p>
                      {user.role === 'LIBRARIAN' && <Badge tone="violet">Librarian</Badge>}
                      {!user.enabled && <Badge tone="red">Blocked</Badge>}
                      {isMe && <Badge tone="slate">You</Badge>}
                    </div>
                    <p className="truncate text-sm text-ink-soft">{user.email}</p>
                    <p className="text-xs text-ink-faint">Joined {formatDate(user.createdAt)}</p>
                  </div>
                  {!isMe && (
                    <div className="flex flex-wrap gap-2">
                      <Button
                        variant="secondary"
                        disabled={update.isPending}
                        onClick={() => changeRole(user, user.role === 'LIBRARIAN' ? 'MEMBER' : 'LIBRARIAN')}
                      >
                        {user.role === 'LIBRARIAN' ? 'Make member' : 'Make librarian'}
                      </Button>
                      <Button variant={user.enabled ? 'danger' : 'secondary'} disabled={update.isPending} onClick={() => toggleBlocked(user)}>
                        {user.enabled ? 'Block' : 'Unblock'}
                      </Button>
                    </div>
                  )}
                </li>
              )
            })}
          </ul>
          <Pagination page={users.data.page} totalPages={users.data.totalPages} onChange={setPage} />
        </>
      )}
      {toast}
    </>
  )
}
