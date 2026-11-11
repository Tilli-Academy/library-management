import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'

import { BookCover, Button, ErrorAlert, Modal, TextArea, TextField } from '../../components/ui'
import { api, coverUrl, serverFieldError } from '../../lib/api'
import { bookSchema, issueSchema, type BookForm, type IssueForm } from '../../lib/schemas'
import type { Book, Loan } from '../../lib/types'

function toForm(book?: Book): BookForm {
  return {
    title: book?.title ?? '',
    author: book?.author ?? '',
    isbn: book?.isbn ?? '',
    genre: book?.genre ?? '',
    description: book?.description ?? '',
    publishedYear: book?.publishedYear ? String(book.publishedYear) : '',
    aboutAuthor: book?.aboutAuthor ?? '',
    totalCopies: String(book && book.status !== 'COMING_SOON' ? book.totalCopies : 1),
    comingSoon: book?.status === 'COMING_SOON',
    expectedArrival: book?.expectedArrival ?? '',
  }
}

/** Add a new book, or edit an existing one (librarians only — the server enforces this too). */
export function BookFormModal({ book, onClose, onSaved }: { book?: Book; onClose: () => void; onSaved: (message: string) => void }) {
  const queryClient = useQueryClient()
  const { register, handleSubmit, formState, control } = useForm<BookForm>({
    resolver: zodResolver(bookSchema),
    defaultValues: toForm(book),
  })
  const comingSoon = useWatch({ control, name: 'comingSoon' })
  const [coverFile, setCoverFile] = useState<File | null>(null)
  const [removeCover, setRemoveCover] = useState(false)

  const save = useMutation({
    mutationFn: async (form: BookForm) => {
      const body = {
        title: form.title,
        author: form.author,
        isbn: form.isbn || null,
        genre: form.genre || null,
        description: form.description || null,
        aboutAuthor: form.aboutAuthor || null,
        publishedYear: form.publishedYear ? Number(form.publishedYear) : null,
        totalCopies: form.comingSoon ? 0 : Number(form.totalCopies),
        comingSoon: form.comingSoon,
        expectedArrival: form.comingSoon && form.expectedArrival ? form.expectedArrival : null,
      }
      const saved = await (book ? api.put<Book>(`/admin/books/${book.id}`, body) : api.post<Book>('/admin/books', body))
      // The book itself is saved now. The cover is a second step; if only that fails, say so clearly.
      try {
        if (coverFile) {
          const upload = new FormData()
          upload.append('file', coverFile)
          await api.putForm<Book>(`/admin/books/${saved.id}/cover`, upload)
        } else if (removeCover && book?.coverVersion != null) {
          await api.delete(`/admin/books/${saved.id}/cover`)
        }
      } catch (error) {
        const reason = error instanceof Error ? error.message : 'Unknown error.'
        return { saved, coverProblem: reason }
      }
      return { saved, coverProblem: null }
    },
    onSuccess: async ({ saved, coverProblem }) => {
      await queryClient.invalidateQueries({ queryKey: ['books'] })
      const done = book ? `Saved “${saved.title}”.` : `Added “${saved.title}” to the catalogue.`
      onSaved(coverProblem ? `${done} But the cover couldn't be saved: ${coverProblem} Edit the book to try again.` : done)
    },
  })

  const err = (field: keyof BookForm) => formState.errors[field]?.message ?? serverFieldError(save.error, field)

  return (
    <Modal title={book ? 'Edit book' : 'Add a book'} onClose={onClose}>
      <form onSubmit={handleSubmit((form) => save.mutate(form))} noValidate className="space-y-4">
        <ErrorAlert error={save.error} />
        <TextField label="Title" {...register('title')} error={err('title')} maxLength={200} />
        <TextField label="Author" {...register('author')} error={err('author')} maxLength={150} />
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="ISBN (optional)" inputMode="numeric" {...register('isbn')} error={err('isbn')} maxLength={17} />
          <TextField label="Genre (optional)" {...register('genre')} error={err('genre')} maxLength={60} />
          <TextField label="Year (optional)" inputMode="numeric" {...register('publishedYear')} error={err('publishedYear')} maxLength={4} />
          {comingSoon ? (
            <TextField label="Expected arrival (optional)" type="date" {...register('expectedArrival')} error={err('expectedArrival')} />
          ) : (
            <TextField
              label="Copies owned"
              inputMode="numeric"
              {...register('totalCopies')}
              error={err('totalCopies')}
              hint={book && book.status !== 'COMING_SOON' ? `${book.totalCopies - book.availableCopies} currently on loan` : undefined}
            />
          )}
        </div>
        <label className="flex items-start gap-3 rounded-lg bg-subtle p-3 text-sm">
          <input type="checkbox" className="mt-0.5 size-4 accent-brand-700" {...register('comingSoon')} />
          <span>
            <span className="font-semibold text-ink">Coming soon</span>
            <span className="block text-ink-soft">
              Show it in the catalogue before the copies arrive. Members can see it but can't borrow it yet. Untick
              this and enter the number of copies when they arrive.
            </span>
          </span>
        </label>
        <CoverPicker
          title={book?.title ?? 'New book'}
          currentUrl={book && !removeCover ? coverUrl(book) : null}
          file={coverFile}
          onFile={(file) => {
            setCoverFile(file)
            setRemoveCover(false)
          }}
          onRemove={() => {
            setCoverFile(null)
            setRemoveCover(true)
          }}
        />
        <TextArea label="About the book (optional)" rows={4} {...register('description')} error={err('description')} maxLength={2000} />
        <TextArea
          label="About the author (optional)"
          rows={3}
          {...register('aboutAuthor')}
          error={err('aboutAuthor')}
          maxLength={2000}
          hint="A short biography: where they're from, other books they wrote…"
        />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            {book ? 'Save changes' : 'Add book'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

const MAX_COVER_BYTES = 3 * 1024 * 1024

/** Choose a cover picture: shows a preview, checks type and size before anything is uploaded. */
function CoverPicker({
  title,
  currentUrl,
  file,
  onFile,
  onRemove,
}: {
  title: string
  currentUrl: string | null
  file: File | null
  onFile: (file: File | null) => void
  onRemove: () => void
}) {
  const [problem, setProblem] = useState<string | null>(null)
  const previewUrl = useMemo(() => (file ? URL.createObjectURL(file) : null), [file])
  useEffect(() => () => {
    if (previewUrl) URL.revokeObjectURL(previewUrl)
  }, [previewUrl])

  async function choose(selected: File | undefined) {
    setProblem(null)
    if (!selected) return
    if (!['image/jpeg', 'image/png'].includes(selected.type)) {
      setProblem('Choose a JPG or PNG picture.')
      return
    }
    if (selected.size > MAX_COVER_BYTES) {
      setProblem('The picture must be 3 MB or smaller.')
      return
    }
    // The file name and type can lie (e.g. a script renamed to .png): check that it really opens as a picture.
    // (The server checks again and redraws it; this just gives a quick, clear message.)
    try {
      const bitmap = await createImageBitmap(selected)
      bitmap.close()
    } catch {
      setProblem("This file isn't a valid JPG or PNG picture.")
      return
    }
    onFile(selected)
  }

  const shown = previewUrl ?? currentUrl
  return (
    <div className="flex items-start gap-4 rounded-lg bg-subtle p-3">
      <BookCover title={title} src={shown} eager className="h-28 w-20 shrink-0" />
      <div className="min-w-0 flex-1 text-sm">
        <p className="font-semibold text-ink">Cover picture (optional)</p>
        <p className="mb-2 text-ink-soft">JPG or PNG, up to 3 MB. It is resized automatically.</p>
        <div className="flex flex-wrap gap-2">
          <label className="inline-flex min-h-10 cursor-pointer items-center rounded-lg bg-surface px-3 py-2 font-semibold ring-1 ring-line hover:bg-page">
            {shown ? 'Change picture' : 'Choose picture'}
            <input
              type="file"
              accept="image/jpeg,image/png"
              className="sr-only"
              onChange={(event) => {
                void choose(event.target.files?.[0])
                event.target.value = ''
              }}
            />
          </label>
          {shown && (
            <Button variant="ghost" onClick={onRemove}>
              Remove
            </Button>
          )}
        </div>
        {problem && (
          <p role="alert" className="mt-1 text-red-700 dark:text-red-400">
            {problem}
          </p>
        )}
      </div>
    </div>
  )
}

/** Lend a copy to a member, identified by their email. */
export function IssueLoanModal({ book, onClose, onIssued }: { book: Book; onClose: () => void; onIssued: (message: string) => void }) {
  const queryClient = useQueryClient()
  const { register, handleSubmit, formState } = useForm<IssueForm>({ resolver: zodResolver(issueSchema) })
  const issue = useMutation({
    mutationFn: (form: IssueForm) => api.post<Loan>('/admin/loans', { bookId: book.id, memberEmail: form.memberEmail }),
    onSuccess: async (loan) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['books'] }),
        queryClient.invalidateQueries({ queryKey: ['admin'] }),
      ])
      onIssued(`Lent “${loan.bookTitle}” to ${loan.memberName}.`)
    },
  })

  return (
    <Modal title={`Lend “${book.title}”`} onClose={onClose}>
      <form onSubmit={handleSubmit((form) => issue.mutate(form))} noValidate className="space-y-4">
        <p className="text-sm text-ink-soft">
          {book.availableCopies} of {book.totalCopies} copies on the shelf. The due date is set automatically from the library's loan period.
        </p>
        <ErrorAlert error={issue.error} />
        <TextField
          label="Member email"
          type="email"
          autoComplete="off"
          {...register('memberEmail')}
          error={formState.errors.memberEmail?.message ?? serverFieldError(issue.error, 'memberEmail')}
        />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={issue.isPending}>
            Lend book
          </Button>
        </div>
      </form>
    </Modal>
  )
}
