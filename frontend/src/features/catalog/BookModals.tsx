import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'

import { Button, ErrorAlert, Modal, TextArea, TextField } from '../../components/ui'
import { api, serverFieldError } from '../../lib/api'
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
    totalCopies: String(book?.totalCopies ?? 1),
  }
}

/** Add a new book, or edit an existing one (librarians only — the server enforces this too). */
export function BookFormModal({ book, onClose, onSaved }: { book?: Book; onClose: () => void; onSaved: (message: string) => void }) {
  const queryClient = useQueryClient()
  const { register, handleSubmit, formState } = useForm<BookForm>({
    resolver: zodResolver(bookSchema),
    defaultValues: toForm(book),
  })

  const save = useMutation({
    mutationFn: (form: BookForm) => {
      const body = {
        title: form.title,
        author: form.author,
        isbn: form.isbn || null,
        genre: form.genre || null,
        description: form.description || null,
        publishedYear: form.publishedYear ? Number(form.publishedYear) : null,
        totalCopies: Number(form.totalCopies),
      }
      return book ? api.put<Book>(`/admin/books/${book.id}`, body) : api.post<Book>('/admin/books', body)
    },
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: ['books'] })
      onSaved(book ? `Saved “${saved.title}”.` : `Added “${saved.title}” to the catalogue.`)
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
          <TextField
            label="Copies owned"
            inputMode="numeric"
            {...register('totalCopies')}
            error={err('totalCopies')}
            hint={book ? `${book.totalCopies - book.availableCopies} currently on loan` : undefined}
          />
        </div>
        <TextArea label="Description (optional)" rows={4} {...register('description')} error={err('description')} maxLength={2000} />
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
