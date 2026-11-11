import { zodResolver } from '@hookform/resolvers/zod'
import { ArrowLeft, BookOpen, Library, UserRound } from 'lucide-react'
import type { ReactNode } from 'react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useLocation, useSearchParams } from 'react-router'

import { Button, ErrorAlert, TextField } from '../components/ui'
import { serverFieldError } from '../lib/api'
import { loginSchema, registerSchema, type LoginForm, type RegisterForm } from '../lib/schemas'
import { useAuth } from './AuthContext'

/** Keeps the "page you wanted" (if any) when moving between welcome / sign-in / sign-up. */
function useCarryState() {
  return useLocation().state as { from?: unknown } | null
}

function AuthShell({ title, subtitle, children, footer }: { title: string; subtitle: string; children: ReactNode; footer: ReactNode }) {
  const state = useCarryState()
  return (
    <main className="flex min-h-dvh items-center justify-center px-4 py-10">
      <div className="w-full max-w-sm">
        <Link to="/welcome" state={state} className="mb-6 inline-flex items-center gap-1 text-sm text-ink-soft hover:text-ink">
          <ArrowLeft className="size-4" aria-hidden /> Back
        </Link>
        <div className="mb-8 flex items-center justify-center gap-2">
          <div className="grid size-10 place-items-center rounded-xl bg-brand-700 text-white">
            <BookOpen className="size-5" aria-hidden />
          </div>
          <span className="font-display text-2xl font-bold">PageTurn</span>
        </div>
        <div className="rounded-2xl bg-surface p-6 shadow-sm ring-1 ring-line sm:p-8">
          <h1 className="font-display text-xl font-bold">{title}</h1>
          <p className="mt-1 mb-6 text-sm text-ink-soft">{subtitle}</p>
          {children}
        </div>
        <p className="mt-6 text-center text-sm text-ink-soft">{footer}</p>
      </div>
    </main>
  )
}

/** First screen: are you a librarian or a member? */
export function WelcomePage() {
  const state = useCarryState()
  const choices = [
    {
      to: '/login?as=librarian',
      icon: Library,
      title: "I'm a librarian",
      text: 'Add books, lend and receive them, and follow up on overdue loans.',
    },
    {
      to: '/login?as=member',
      icon: UserRound,
      title: "I'm a member",
      text: 'Check if a book is available, when it will be back, and see your borrowed books.',
    },
  ]
  return (
    <main className="flex min-h-dvh items-center justify-center px-4 py-10">
      <div className="w-full max-w-2xl">
        <div className="mb-3 flex items-center justify-center gap-2">
          <div className="grid size-11 place-items-center rounded-xl bg-brand-700 text-white">
            <BookOpen className="size-6" aria-hidden />
          </div>
          <span className="font-display text-3xl font-bold">PageTurn</span>
        </div>
        <p className="mb-10 text-center text-ink-soft">Welcome to the library. Who are you?</p>
        <div className="grid gap-4 sm:grid-cols-2">
          {choices.map((choice) => (
            <Link
              key={choice.to}
              to={choice.to}
              state={state}
              className="group rounded-2xl bg-surface p-6 shadow-sm ring-1 ring-line transition hover:-translate-y-0.5 hover:shadow-md hover:ring-brand-600 focus-visible:outline-2 focus-visible:outline-brand-600"
            >
              <div className="mb-4 grid size-12 place-items-center rounded-xl bg-brand-50 text-brand-700 dark:bg-brand-950 dark:text-brand-300">
                <choice.icon className="size-6" aria-hidden />
              </div>
              <h2 className="font-display text-lg font-bold">{choice.title}</h2>
              <p className="mt-1 text-sm text-ink-soft">{choice.text}</p>
            </Link>
          ))}
        </div>
      </div>
    </main>
  )
}

export function LoginPage() {
  const { signIn } = useAuth()
  const state = useCarryState()
  const [params] = useSearchParams()
  const asLibrarian = params.get('as') === 'librarian'
  const [error, setError] = useState<unknown>(null)
  const { register, handleSubmit, formState } = useForm<LoginForm>({ resolver: zodResolver(loginSchema) })

  const onSubmit = handleSubmit(async (form) => {
    setError(null)
    try {
      // On success the RedirectIfSignedIn guard sends the user on to where they wanted to go.
      await signIn(form.email, form.password, asLibrarian ? 'LIBRARIAN' : undefined)
    } catch (e) {
      setError(e)
    }
  })

  return (
    <AuthShell
      title={asLibrarian ? 'Librarian sign in' : 'Member sign in'}
      subtitle={asLibrarian ? 'Manage books, loans and members.' : 'Check availability and see your books.'}
      footer={
        asLibrarian ? (
          // Librarian accounts are never self-made: the head librarian gives librarian access.
          'Librarian accounts are created by the head librarian.'
        ) : (
          <>
            New member?{' '}
            <Link to="/register" state={state} className="font-semibold text-brand-700 hover:underline dark:text-brand-300">
              Create an account
            </Link>
          </>
        )
      }
    >
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        <ErrorAlert error={error} />
        <TextField label="Email" type="email" autoComplete="email" {...register('email')} error={formState.errors.email?.message} />
        <TextField
          label="Password"
          type="password"
          autoComplete="current-password"
          {...register('password')}
          error={formState.errors.password?.message}
        />
        <Button type="submit" className="w-full" loading={formState.isSubmitting}>
          Sign in
        </Button>
      </form>
    </AuthShell>
  )
}

export function RegisterPage() {
  const { signUp } = useAuth()
  const state = useCarryState()
  const [error, setError] = useState<unknown>(null)
  const { register, handleSubmit, formState } = useForm<RegisterForm>({ resolver: zodResolver(registerSchema) })

  const onSubmit = handleSubmit(async (form) => {
    setError(null)
    try {
      await signUp(form.displayName, form.phone, form.email, form.password)
    } catch (e) {
      setError(e)
    }
  })

  return (
    <AuthShell
      title="Create your member account"
      subtitle="Check which books are available and keep track of what you borrowed."
      footer={
        <>
          Already a member?{' '}
          <Link to="/login?as=member" state={state} className="font-semibold text-brand-700 hover:underline dark:text-brand-300">
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        <ErrorAlert error={error} />
        <TextField
          label="Your name"
          autoComplete="name"
          {...register('displayName')}
          error={formState.errors.displayName?.message ?? serverFieldError(error, 'displayName')}
        />
        <TextField
          label="Phone number"
          type="tel"
          autoComplete="tel"
          hint="The library calls this number if a book is overdue."
          {...register('phone')}
          error={formState.errors.phone?.message ?? serverFieldError(error, 'phone')}
        />
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          {...register('email')}
          error={formState.errors.email?.message ?? serverFieldError(error, 'email')}
        />
        <TextField
          label="Password"
          type="password"
          autoComplete="new-password"
          hint="At least 12 characters. A short sentence is easy to remember."
          {...register('password')}
          error={formState.errors.password?.message ?? serverFieldError(error, 'password')}
        />
        <TextField
          label="Confirm password"
          type="password"
          autoComplete="new-password"
          {...register('confirmPassword')}
          error={formState.errors.confirmPassword?.message}
        />
        <Button type="submit" className="w-full" loading={formState.isSubmitting}>
          Create account
        </Button>
      </form>
    </AuthShell>
  )
}
