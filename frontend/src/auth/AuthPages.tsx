import { zodResolver } from '@hookform/resolvers/zod'
import { ArrowLeft, ArrowRight } from 'lucide-react'
import type { ReactNode } from 'react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useLocation, useSearchParams } from 'react-router'

import { LibraryBackdrop } from '../components/LibraryBackdrop'
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
    <LibraryBackdrop>
      <main className="flex flex-1 items-center justify-center px-4 py-10">
        <div className="w-full max-w-sm">
          <Link to="/welcome" state={state} className="mb-6 inline-flex items-center gap-1 text-sm text-[#f3eee3]/80 hover:text-[#f3eee3]">
            <ArrowLeft className="size-4" aria-hidden /> Back
          </Link>
          <p className="mb-6 text-center font-elegant text-4xl text-[#f3eee3]">
            <span className="italic">PageTurn</span> <span className="tracking-wide uppercase">Library</span>
          </p>
          <div className="rounded-2xl bg-surface/95 p-6 text-ink shadow-2xl ring-1 ring-black/10 backdrop-blur sm:p-8">
            <h1 className="font-display text-xl font-bold">{title}</h1>
            <p className="mt-1 mb-6 text-sm text-ink-soft">{subtitle}</p>
            {children}
          </div>
          <p className="mt-6 text-center text-sm text-[#f3eee3]/85 [&_a]:text-[#f3eee3] [&_a]:underline">{footer}</p>
        </div>
      </main>
    </LibraryBackdrop>
  )
}

/** First screen, styled like a grand old library: are you a librarian or a member? */
export function WelcomePage() {
  const state = useCarryState()
  const choices = [
    { to: '/login?as=member', title: "I'm a member", text: 'Find books, see when they are back, request new ones' },
    { to: '/login?as=librarian', title: "I'm a librarian", text: 'Manage books, loans and members' },
  ]
  return (
    <LibraryBackdrop>
      <main className="flex flex-1 flex-col items-center justify-center px-4 pt-16 pb-14 text-center">
        <h1 className="font-elegant leading-[0.85] drop-shadow-[0_2px_12px_rgba(0,0,0,0.5)]">
          <span className="block text-7xl italic sm:text-8xl md:text-9xl">PageTurn</span>
          <span className="mt-2 block text-6xl tracking-wide uppercase sm:text-8xl md:text-[8.5rem]">Library</span>
        </h1>
        <p className="mt-8 rounded-full border border-[#f3eee3]/80 px-6 py-2 font-elegant text-lg tracking-wide uppercase sm:px-10 sm:text-2xl">
          A journey through books
        </p>

        <nav aria-label="Who are you?" className="mt-12 grid w-full max-w-2xl gap-4 sm:grid-cols-2">
          {choices.map((choice) => (
            <Link
              key={choice.to}
              to={choice.to}
              state={state}
              className="group flex items-center justify-between gap-4 rounded-2xl border border-[#f3eee3]/40 bg-black/30 px-6 py-5 text-left backdrop-blur-sm transition hover:border-[#f3eee3] hover:bg-black/45 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#f3eee3]"
            >
              <span>
                <span className="block font-elegant text-2xl font-semibold">{choice.title}</span>
                <span className="mt-0.5 block text-sm text-[#f3eee3]/75">{choice.text}</span>
              </span>
              <ArrowRight className="size-6 shrink-0 transition group-hover:translate-x-1" aria-hidden />
            </Link>
          ))}
        </nav>
      </main>
    </LibraryBackdrop>
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
