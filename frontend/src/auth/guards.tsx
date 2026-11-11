import { Navigate, Outlet, useLocation } from 'react-router'

import { Spinner } from '../components/ui'
import { safeRedirect } from '../lib/redirect'
import { useAuth } from './AuthContext'

/**
 * Route guards only decide which SCREENS to show. They are a convenience, not the security boundary:
 * the server checks the session and the role on every single request.
 */
export function RequireAuth() {
  const { user, isLoading } = useAuth()
  const location = useLocation()
  if (isLoading) return <Spinner />
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <Outlet />
}

export function RequireLibrarian() {
  const { isLibrarian } = useAuth()
  return isLibrarian ? <Outlet /> : <Navigate to="/" replace />
}

/**
 * Sign-in and sign-up pages. Once the user is signed in, this is the ONE place that sends them on —
 * back to the page they originally asked for (only if it's inside this app), otherwise the catalogue.
 */
export function RedirectIfSignedIn() {
  const { user, isLoading } = useAuth()
  const location = useLocation()
  if (isLoading) return <Spinner />
  if (user) return <Navigate to={safeRedirect((location.state as { from?: unknown } | null)?.from)} replace />
  return <Outlet />
}
