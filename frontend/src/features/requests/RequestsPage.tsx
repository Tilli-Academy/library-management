import { zodResolver } from '@hookform/resolvers/zod'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, Heart, Lightbulb, Plus, X } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'

import { useAuth } from '../../auth/AuthContext'
import { useToast } from '../../components/Toast'
import { Badge, Button, EmptyState, ErrorAlert, Modal, PageHeader, Pagination, Spinner, TextArea, TextField } from '../../components/ui'
import { api, query, serverFieldError } from '../../lib/api'
import { formatDate } from '../../lib/format'
import { suggestionSchema, type SuggestionForm } from '../../lib/schemas'
import type { Page, Suggestion, SuggestionStatus } from '../../lib/types'

const tabs: { value: SuggestionStatus; label: string }[] = [
  { value: 'OPEN', label: 'Collecting likes' },
  { value: 'APPROVED', label: 'Coming to the library' },
  { value: 'DECLINED', label: 'Declined' },
]

/**
 * Members ask for books the library doesn't have, and like each other's requests.
 * When a request gets enough likes, the book is added to the catalogue as "coming soon".
 */
export function RequestsPage() {
  const { isLibrarian } = useAuth()
  const queryClient = useQueryClient()
  const { toast, show } = useToast()
  const [status, setStatus] = useState<SuggestionStatus>('OPEN')
  const [page, setPage] = useState(0)
  const [asking, setAsking] = useState(false)

  const list = useQuery({
    queryKey: ['suggestions', { status, page }],
    queryFn: () => api.get<Page<Suggestion>>(`/suggestions${query({ status, page, size: 20 })}`),
    placeholderData: keepPreviousData,
  })

  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['suggestions'] }),
      queryClient.invalidateQueries({ queryKey: ['books'] }),
    ])

  const toggleLike = useMutation({
    mutationFn: (s: Suggestion) =>
      s.likedByMe ? api.delete(`/suggestions/${s.id}/like`).then(() => null) : api.post<Suggestion>(`/suggestions/${s.id}/like`),
    onSuccess: async (updated) => {
      await refresh()
      if (updated?.status === 'APPROVED') show(`“${updated.title}” got enough likes — it's coming to the library!`)
    },
  })

  const decide = useMutation({
    mutationFn: ({ s, action }: { s: Suggestion; action: 'approve' | 'decline' }) =>
      api.post<Suggestion>(`/admin/suggestions/${s.id}/${action}`),
    onSuccess: async (updated) => {
      await refresh()
      show(updated.status === 'APPROVED' ? `“${updated.title}” added to the catalogue as coming soon.` : `Declined “${updated.title}”.`)
    },
  })

  return (
    <>
      <PageHeader
        title="Book requests"
        subtitle="Missing a book? Request it. Like the requests you want too — popular ones are added to the library."
        actions={
          <Button onClick={() => setAsking(true)}>
            <Plus className="size-4" aria-hidden /> Request a book
          </Button>
        }
      />

      <div className="mb-6 flex flex-wrap rounded-xl bg-surface p-1 ring-1 ring-line sm:inline-flex" role="tablist">
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
            className={`flex-1 rounded-lg px-3 py-2 text-sm font-medium ${status === tab.value ? 'bg-brand-700 text-white' : 'text-ink-soft hover:bg-subtle'}`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="mb-4 space-y-2">
        <ErrorAlert error={toggleLike.error} />
        <ErrorAlert error={decide.error} />
      </div>

      {list.isPending ? (
        <Spinner />
      ) : list.isError ? (
        <ErrorAlert error={list.error} title="Couldn't load requests" />
      ) : list.data.items.length === 0 ? (
        <EmptyState icon={<Lightbulb className="size-6" aria-hidden />} title="No requests here yet">
          {status === 'OPEN' ? 'Be the first: request a book you would love to read.' : 'Nothing in this list yet.'}
        </EmptyState>
      ) : (
        <>
          <ul className="space-y-3">
            {list.data.items.map((s) => {
              const percent = Math.min(100, Math.round((s.likeCount / s.likesNeeded) * 100))
              return (
                <li key={s.id} className="rounded-2xl bg-surface p-4 shadow-sm ring-1 ring-line">
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-start">
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <h2 className="font-display font-bold">{s.title}</h2>
                        {s.mine && <Badge tone="slate">Your request</Badge>}
                        {s.status === 'APPROVED' && <Badge tone="violet">{s.bookStatus === 'AVAILABLE' ? 'In stock now' : 'Coming soon'}</Badge>}
                        {s.status === 'DECLINED' && <Badge tone="red">Declined</Badge>}
                      </div>
                      <p className="text-sm text-ink-soft">{s.author}</p>
                      {s.note && <p className="mt-1 text-sm whitespace-pre-line text-ink-soft">“{s.note}”</p>}
                      <p className="mt-1 text-xs text-ink-faint">
                        Requested {formatDate(s.createdAt)}
                        {s.requestedByName && ` by ${s.requestedByName}`}
                        {s.decidedAt && ` · decided ${formatDate(s.decidedAt)}`}
                      </p>
                      {s.status === 'OPEN' && (
                        <div className="mt-3 max-w-sm">
                          <div className="h-2 overflow-hidden rounded-full bg-subtle" role="progressbar" aria-valuenow={s.likeCount} aria-valuemin={0} aria-valuemax={s.likesNeeded} aria-label="Likes">
                            <div className="h-full rounded-full bg-brand-600" style={{ width: `${percent}%` }} />
                          </div>
                          <p className="mt-1 text-xs text-ink-soft">
                            {s.likeCount} of {s.likesNeeded} likes — {Math.max(0, s.likesNeeded - s.likeCount)} more to get it into the library
                          </p>
                        </div>
                      )}
                    </div>
                    {s.status === 'OPEN' && (
                      <div className="flex flex-wrap gap-2">
                        <Button
                          variant={s.likedByMe ? 'primary' : 'secondary'}
                          aria-pressed={s.likedByMe}
                          onClick={() => toggleLike.mutate(s)}
                          loading={toggleLike.isPending && toggleLike.variables?.id === s.id}
                        >
                          <Heart className={`size-4 ${s.likedByMe ? 'fill-current' : ''}`} aria-hidden />
                          {s.likedByMe ? 'Liked' : 'Like'} · {s.likeCount}
                        </Button>
                        {isLibrarian && (
                          <>
                            <Button variant="secondary" onClick={() => decide.mutate({ s, action: 'approve' })} disabled={decide.isPending}>
                              <Check className="size-4" aria-hidden /> Get it
                            </Button>
                            <Button
                              variant="danger"
                              onClick={() => {
                                if (window.confirm(`Decline the request for “${s.title}”?`)) decide.mutate({ s, action: 'decline' })
                              }}
                              disabled={decide.isPending}
                            >
                              <X className="size-4" aria-hidden /> Decline
                            </Button>
                          </>
                        )}
                      </div>
                    )}
                  </div>
                </li>
              )
            })}
          </ul>
          <Pagination page={list.data.page} totalPages={list.data.totalPages} onChange={setPage} />
        </>
      )}

      {asking && (
        <RequestModal
          onClose={() => setAsking(false)}
          onCreated={(s) => {
            setAsking(false)
            setStatus(s.status)
            setPage(0)
            show(s.status === 'APPROVED' ? `“${s.title}” is coming to the library!` : `Requested “${s.title}”. Ask friends to like it!`)
          }}
        />
      )}
      {toast}
    </>
  )
}

function RequestModal({ onClose, onCreated }: { onClose: () => void; onCreated: (s: Suggestion) => void }) {
  const queryClient = useQueryClient()
  const { register, handleSubmit, formState } = useForm<SuggestionForm>({
    resolver: zodResolver(suggestionSchema),
    defaultValues: { title: '', author: '', note: '' },
  })
  const create = useMutation({
    mutationFn: (form: SuggestionForm) =>
      api.post<Suggestion>('/suggestions', { title: form.title, author: form.author, note: form.note || null }),
    onSuccess: async (created) => {
      await queryClient.invalidateQueries({ queryKey: ['suggestions'] })
      onCreated(created)
    },
  })
  const err = (field: keyof SuggestionForm) => formState.errors[field]?.message ?? serverFieldError(create.error, field)

  return (
    <Modal title="Request a book" onClose={onClose}>
      <form onSubmit={handleSubmit((form) => create.mutate(form))} noValidate className="space-y-4">
        <p className="text-sm text-ink-soft">Your request counts as the first like. Other members can like it too.</p>
        <ErrorAlert error={create.error} />
        <TextField label="Title" {...register('title')} error={err('title')} maxLength={200} />
        <TextField label="Author" {...register('author')} error={err('author')} maxLength={150} />
        <TextArea label="Why should the library get it? (optional)" rows={3} {...register('note')} error={err('note')} maxLength={500} />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={create.isPending}>
            Send request
          </Button>
        </div>
      </form>
    </Modal>
  )
}
