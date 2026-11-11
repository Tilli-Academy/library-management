import { AlertCircle, BookOpen, ChevronLeft, ChevronRight, Loader2, X } from 'lucide-react'
import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, TextareaHTMLAttributes } from 'react'
import { forwardRef, useEffect, useId, useRef } from 'react'

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost'

const variantClasses: Record<Variant, string> = {
  primary: 'bg-brand-700 text-white hover:bg-brand-600 shadow-sm',
  secondary:
    'bg-surface text-ink ring-1 ring-line hover:bg-subtle shadow-sm',
  danger: 'bg-surface text-red-700 ring-1 ring-red-300 hover:bg-red-50 dark:text-red-300 dark:ring-red-800 dark:hover:bg-red-950',
  ghost: 'text-ink-soft hover:bg-subtle',
}

export function Button({
  variant = 'primary',
  className = '',
  loading = false,
  children,
  disabled,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; loading?: boolean }) {
  return (
    <button
      type="button"
      {...props}
      disabled={disabled || loading}
      className={`inline-flex min-h-10 items-center justify-center gap-1.5 rounded-lg px-3.5 py-2 text-sm font-semibold transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600 disabled:cursor-not-allowed disabled:opacity-50 ${variantClasses[variant]} ${className}`}
    >
      {loading && <Loader2 className="size-4 animate-spin" aria-hidden />}
      {children}
    </button>
  )
}

const inputClasses =
  'block w-full rounded-lg border-0 bg-surface px-3 py-2.5 text-sm text-ink ring-1 ring-line placeholder:text-ink-faint focus:ring-2 focus:ring-brand-600 focus:outline-none aria-[invalid=true]:ring-red-500'

interface FieldProps {
  label: string
  error?: string
  hint?: string
}

export const TextField = forwardRef<HTMLInputElement, FieldProps & InputHTMLAttributes<HTMLInputElement>>(
  function TextField({ label, error, hint, ...props }, ref) {
    const id = useId()
    return (
      <div>
        <label htmlFor={id} className="block text-sm font-medium text-ink-soft">
          {label}
        </label>
        <input
          id={id}
          ref={ref}
          {...props}
          aria-invalid={error ? true : undefined}
          aria-describedby={error || hint ? `${id}-msg` : undefined}
          className={`mt-1 ${inputClasses}`}
        />
        <FieldMessage id={`${id}-msg`} error={error} hint={hint} />
      </div>
    )
  },
)

export const TextArea = forwardRef<HTMLTextAreaElement, FieldProps & TextareaHTMLAttributes<HTMLTextAreaElement>>(
  function TextArea({ label, error, hint, ...props }, ref) {
    const id = useId()
    return (
      <div>
        <label htmlFor={id} className="block text-sm font-medium text-ink-soft">
          {label}
        </label>
        <textarea
          id={id}
          ref={ref}
          {...props}
          aria-invalid={error ? true : undefined}
          aria-describedby={error || hint ? `${id}-msg` : undefined}
          className={`mt-1 ${inputClasses}`}
        />
        <FieldMessage id={`${id}-msg`} error={error} hint={hint} />
      </div>
    )
  },
)

function FieldMessage({ id, error, hint }: { id: string; error?: string; hint?: string }) {
  if (error) {
    return (
      <p id={id} className="mt-1 text-sm text-red-700 dark:text-red-400">
        {error}
      </p>
    )
  }
  return hint ? (
    <p id={id} className="mt-1 text-xs text-ink-faint">
      {hint}
    </p>
  ) : null
}

/** Shows an error message. Text is rendered by React, so it's always escaped (never treated as HTML). */
export function ErrorAlert({ error, title }: { error: unknown; title?: string }) {
  if (!error) return null
  const message = error instanceof Error ? error.message : 'Something went wrong.'
  return (
    <div role="alert" className="flex gap-2 rounded-lg bg-red-50 p-3 text-sm text-red-800 ring-1 ring-red-200 dark:bg-red-950/60 dark:text-red-200 dark:ring-red-900">
      <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden />
      <div>
        {title && <p className="font-semibold">{title}</p>}
        <p>{message}</p>
      </div>
    </div>
  )
}

export function Spinner({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-2 py-16 text-ink-faint" role="status">
      <Loader2 className="size-5 animate-spin" aria-hidden />
      <span className="text-sm">{label}</span>
    </div>
  )
}

export function EmptyState({ icon, title, children }: { icon?: ReactNode; title: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-center rounded-2xl border border-dashed border-line px-6 py-14 text-center">
      <div className="mb-3 grid size-12 place-items-center rounded-full bg-brand-50 text-brand-700 dark:bg-brand-950 dark:text-brand-300">
        {icon ?? <BookOpen className="size-6" aria-hidden />}
      </div>
      <h3 className="font-semibold text-ink">{title}</h3>
      {children && <div className="mt-1 max-w-sm text-sm text-ink-soft">{children}</div>}
    </div>
  )
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="font-display text-2xl font-bold tracking-tight text-ink sm:text-3xl">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-ink-soft">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  )
}

export function Badge({ tone, children }: { tone: 'green' | 'amber' | 'red' | 'violet' | 'slate'; children: ReactNode }) {
  const tones = {
    green: 'bg-emerald-50 text-emerald-800 ring-emerald-200 dark:bg-emerald-950 dark:text-emerald-300 dark:ring-emerald-900',
    amber: 'bg-amber-50 text-amber-800 ring-amber-200 dark:bg-amber-950 dark:text-amber-300 dark:ring-amber-900',
    red: 'bg-red-50 text-red-800 ring-red-200 dark:bg-red-950 dark:text-red-300 dark:ring-red-900',
    violet: 'bg-brand-50 text-brand-800 ring-brand-200 dark:bg-brand-950 dark:text-brand-300 dark:ring-brand-900',
    slate: 'bg-subtle text-ink-soft ring-line',
  }
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold ring-1 ${tones[tone]}`}>
      {children}
    </span>
  )
}

/** A generated cover: colour chosen from the title, so the same book always looks the same. */
export function BookCover({ title, className = '' }: { title: string; className?: string }) {
  const palettes = [
    'from-violet-600 to-indigo-700',
    'from-rose-500 to-orange-500',
    'from-emerald-600 to-teal-700',
    'from-amber-500 to-rose-600',
    'from-sky-600 to-blue-800',
    'from-fuchsia-600 to-purple-800',
  ]
  let hash = 0
  for (const char of title) hash = (hash * 31 + char.charCodeAt(0)) >>> 0
  return (
    <div
      aria-hidden
      className={`relative flex flex-col justify-between overflow-hidden rounded-lg bg-gradient-to-br p-3 text-white shadow-inner ${palettes[hash % palettes.length]} ${className}`}
    >
      <div className="absolute inset-y-0 left-1.5 w-px bg-white/30" />
      <BookOpen className="size-5 opacity-80" />
      <p className="line-clamp-3 font-display text-xs leading-tight font-semibold">{title}</p>
    </div>
  )
}

export function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) {
  if (totalPages <= 1) return null
  return (
    <nav className="mt-6 flex items-center justify-center gap-3" aria-label="Pagination">
      <Button variant="secondary" onClick={() => onChange(page - 1)} disabled={page <= 0} aria-label="Previous page">
        <ChevronLeft className="size-4" aria-hidden />
      </Button>
      <span className="text-sm text-ink-soft">
        Page {page + 1} of {totalPages}
      </span>
      <Button variant="secondary" onClick={() => onChange(page + 1)} disabled={page >= totalPages - 1} aria-label="Next page">
        <ChevronRight className="size-4" aria-hidden />
      </Button>
    </nav>
  )
}

/** Accessible dialog: Escape closes it, focus moves inside, the page behind can't be scrolled. */
export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const panel = useRef<HTMLDivElement>(null)
  const titleId = useId()

  useEffect(() => {
    const previouslyFocused = document.activeElement as HTMLElement | null
    const first = panel.current?.querySelector<HTMLElement>('input, textarea, select, button:not([data-close])')
    first?.focus()
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = overflow
      previouslyFocused?.focus()
    }
  }, [onClose])

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 p-0 backdrop-blur-sm sm:items-center sm:p-4"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="max-h-[92dvh] w-full overflow-y-auto rounded-t-2xl bg-surface p-5 shadow-xl sm:max-w-lg sm:rounded-2xl sm:p-6"
      >
        <div className="mb-4 flex items-start justify-between gap-4">
          <h2 id={titleId} className="font-display text-lg font-bold text-ink">
            {title}
          </h2>
          <button
            type="button"
            data-close
            onClick={onClose}
            className="rounded-md p-1 text-ink-faint hover:bg-subtle hover:text-ink"
            aria-label="Close"
          >
            <X className="size-5" aria-hidden />
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}
