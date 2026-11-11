import './index.css'

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router'

import { AuthProvider } from './auth/AuthContext'
import { LoginPage, RegisterPage, WelcomePage } from './auth/AuthPages'
import { RedirectIfSignedIn, RequireAuth, RequireLibrarian } from './auth/guards'
import { Layout } from './components/Layout'
import { NotFound } from './components/NotFound'
import { CatalogPage } from './features/catalog/CatalogPage'
import { MyBooksPage } from './features/loans/MyBooksPage'
import { ApiError } from './lib/api'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      // Don't retry "you're not allowed" or "not found" — only temporary problems.
      retry: (count, error) => !(error instanceof ApiError && error.status >= 400 && error.status < 500) && count < 2,
    },
  },
})

const router = createBrowserRouter(
  [
    {
      element: <RedirectIfSignedIn />,
      children: [
        { path: '/welcome', element: <WelcomePage /> },
        { path: '/login', element: <LoginPage /> },
        { path: '/register', element: <RegisterPage /> },
      ],
    },
    {
      element: <RequireAuth />,
      children: [
        {
          element: <Layout />,
          children: [
            { path: '/', element: <CatalogPage /> },
            { path: '/my-books', element: <MyBooksPage /> },
            {
              element: <RequireLibrarian />,
              children: [
                // Librarian screens are loaded only when a librarian opens them (smaller app for members).
                { path: '/admin', lazy: async () => ({ Component: (await import('./features/admin/DashboardPage')).DashboardPage }) },
                { path: '/admin/loans', lazy: async () => ({ Component: (await import('./features/admin/LoansPage')).LoansPage }) },
                { path: '/admin/users', lazy: async () => ({ Component: (await import('./features/admin/UsersPage')).UsersPage }) },
              ],
            },
            { path: '*', element: <NotFound /> },
          ],
        },
      ],
    },
  ],
  { basename: import.meta.env.BASE_URL.replace(/\/$/, '') || '/' },
)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
)
