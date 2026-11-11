import { useQuery, useQueryClient } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { createContext, useCallback, useContext, useEffect, useMemo } from 'react'

import { api, ApiError, setUnauthorizedHandler } from '../lib/api'
import type { User } from '../lib/types'

interface AuthState {
  user: User | null
  isLoading: boolean
  isLibrarian: boolean
  signIn: (email: string, password: string) => Promise<User>
  signUp: (displayName: string, email: string, password: string) => Promise<User>
  signOut: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)
const ME_KEY = ['me']

async function fetchMe(): Promise<User | null> {
  try {
    return await api.get<User>('/auth/me')
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null
    throw error
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const me = useQuery({ queryKey: ME_KEY, queryFn: fetchMe, retry: false, staleTime: 5 * 60_000 })

  /** Forget everything we loaded for the previous user (their loans etc. must not leak to the next one). */
  const resetSession = useCallback(
    (user: User | null) => {
      queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== ME_KEY[0] })
      queryClient.setQueryData(ME_KEY, user)
    },
    [queryClient],
  )

  useEffect(() => {
    setUnauthorizedHandler(() => resetSession(null))
    return () => setUnauthorizedHandler(null)
  }, [resetSession])

  const signIn = useCallback(
    async (email: string, password: string) => {
      const user = await api.post<User>('/auth/login', { email, password })
      resetSession(user)
      return user
    },
    [resetSession],
  )

  const signUp = useCallback(
    async (displayName: string, email: string, password: string) => {
      const user = await api.post<User>('/auth/register', { displayName, email, password })
      resetSession(user)
      return user
    },
    [resetSession],
  )

  /**
   * Only shows the user as signed out once the server has really ended the session. If the request fails
   * (no connection, server busy), the error is passed on so the user knows — important on shared computers.
   */
  const signOut = useCallback(async () => {
    try {
      await api.post('/auth/logout')
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 401)) throw error
      // 401 = the session had already ended; nothing left to do on the server.
    }
    resetSession(null)
  }, [resetSession])

  const value = useMemo<AuthState>(
    () => ({
      user: me.data ?? null,
      isLoading: me.isPending,
      isLibrarian: me.data?.role === 'LIBRARIAN',
      signIn,
      signUp,
      signOut,
    }),
    [me.data, me.isPending, signIn, signUp, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>')
  return context
}
