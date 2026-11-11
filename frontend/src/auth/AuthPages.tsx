import { zodResolver } from '@hookform/resolvers/zod'
import { BookOpen } from 'lucide-react'
import type { ReactNode } from 'react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'

import { Button, ErrorAlert, TextField } from '../components/ui'
import { serverFieldError } from '../lib/api'
import { loginSchema, registerSchema, type LoginForm, type RegisterForm } from '../lib/schemas'
import { useAuth } from './AuthContext'

function AuthShell({ title, subtitle, children, footer }: { title: string; subtitle: string; children: ReactNode; footer: ReactNode }) {
  return (
    <main className="flex min-h-dvh items-center justify-center px-4 py-10">
      <div className="w-full max-w-sm">
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

export function LoginPage() {
  const { signIn } = useAuth()
  const [error, setError] = useState<unknown>(null)
  const { register, handleSubmit, formState } = useForm<LoginForm>({ resolver: zodResolver(loginSchema) })

  const onSubmit = handleSubmit(async (form) => {
    setError(null)
    try {
      // On success the RedirectIfSignedIn guard sends the user on to where they wanted to go.
      await signIn(form.email, form.password)
    } catch (e) {
      setError(e)
    }
  })

  return (
    <AuthShell
      title="Welcome back"
      subtitle="Sign in to browse the catalogue and see your books."
      footer={
        <>
          New here?{' '}
          <Link to="/register" className="font-semibold text-brand-700 hover:underline dark:text-brand-300">
            Create an account
          </Link>
        </>
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
  const [error, setError] = useState<unknown>(null)
  const { register, handleSubmit, formState } = useForm<RegisterForm>({ resolver: zodResolver(registerSchema) })

  const onSubmit = handleSubmit(async (form) => {
    setError(null)
    try {
      await signUp(form.displayName, form.email, form.password)
    } catch (e) {
      setError(e)
    }
  })

  return (
    <AuthShell
      title="Create your account"
      subtitle="Members can browse the catalogue and track their loans."
      footer={
        <>
          Already a member?{' '}
          <Link to="/login" className="font-semibold text-brand-700 hover:underline dark:text-brand-300">
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
