import { BookMarked, BookOpen, LayoutDashboard, Library, Lightbulb, LogOut, Repeat, Users } from 'lucide-react'
import type { ComponentType } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router'

import { useAuth } from '../auth/AuthContext'

interface NavItem {
  to: string
  label: string
  icon: ComponentType<{ className?: string }>
  end?: boolean
}

const memberNav: NavItem[] = [
  { to: '/', label: 'Catalogue', icon: Library, end: true },
  { to: '/my-books', label: 'My books', icon: BookMarked },
  { to: '/requests', label: 'Requests', icon: Lightbulb },
]

const librarianNav: NavItem[] = [
  { to: '/admin', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/admin/loans', label: 'Loans', icon: Repeat },
  { to: '/admin/users', label: 'Members', icon: Users },
]

/** Sidebar on wide screens, bottom tab bar on phones (like a native app). */
export function Layout() {
  const { user, isLibrarian, signOut } = useAuth()
  const navigate = useNavigate()
  const items = isLibrarian ? [...memberNav, ...librarianNav] : memberNav

  async function handleSignOut() {
    try {
      await signOut()
      navigate('/welcome', { replace: true })
    } catch (error) {
      const reason = error instanceof Error ? error.message : 'Unknown error.'
      window.alert(`You are NOT signed out yet: ${reason} Please try again.`)
    }
  }

  return (
    <div className="min-h-dvh md:flex">
      <aside className="hidden w-64 shrink-0 flex-col border-r border-line bg-surface px-4 py-6 md:flex">
        <div className="mb-8 flex items-center gap-2 px-2">
          <div className="grid size-9 place-items-center rounded-xl bg-brand-700 text-white">
            <BookOpen className="size-5" aria-hidden />
          </div>
          <span className="font-display text-xl font-bold">PageTurn</span>
        </div>
        <nav className="flex flex-1 flex-col gap-1" aria-label="Main">
          {memberNav.map((item) => (
            <SideLink key={item.to} item={item} />
          ))}
          {isLibrarian && (
            <>
              <p className="mt-6 mb-1 px-3 text-xs font-semibold tracking-wider text-ink-faint uppercase">Librarian</p>
              {librarianNav.map((item) => (
                <SideLink key={item.to} item={item} />
              ))}
            </>
          )}
        </nav>
        <div className="mt-6 border-t border-line pt-4">
          <div className="flex items-center gap-3 px-2">
            <div className="grid size-9 shrink-0 place-items-center rounded-full bg-brand-50 font-semibold text-brand-800 dark:bg-brand-950 dark:text-brand-300">
              {user?.displayName.charAt(0).toUpperCase()}
            </div>
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold">{user?.displayName}</p>
              <p className="truncate text-xs text-ink-faint">{isLibrarian ? 'Librarian' : 'Member'}</p>
            </div>
          </div>
          <button
            type="button"
            onClick={handleSignOut}
            className="mt-3 flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm text-ink-soft hover:bg-subtle"
          >
            <LogOut className="size-4" aria-hidden /> Sign out
          </button>
        </div>
      </aside>

      <header className="sticky top-0 z-30 flex items-center justify-between border-b border-line bg-surface/90 px-4 py-3 backdrop-blur md:hidden">
        <div className="flex items-center gap-2">
          <div className="grid size-8 place-items-center rounded-lg bg-brand-700 text-white">
            <BookOpen className="size-4" aria-hidden />
          </div>
          <span className="font-display text-lg font-bold">PageTurn</span>
        </div>
        <button type="button" onClick={handleSignOut} className="rounded-lg p-2 text-ink-soft hover:bg-subtle" aria-label="Sign out">
          <LogOut className="size-5" aria-hidden />
        </button>
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 pt-6 pb-28 sm:px-6 md:px-10 md:pt-10 md:pb-10">
        <Outlet />
      </main>

      <nav
        className="fixed inset-x-0 bottom-0 z-30 flex border-t border-line bg-surface/95 pb-[env(safe-area-inset-bottom)] backdrop-blur md:hidden"
        aria-label="Main"
      >
        {items.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) =>
              `flex flex-1 flex-col items-center gap-0.5 py-2 text-[11px] font-medium ${isActive ? 'text-brand-700 dark:text-brand-300' : 'text-ink-faint'}`
            }
          >
            <item.icon className="size-5" aria-hidden />
            {item.label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}

function SideLink({ item }: { item: NavItem }) {
  return (
    <NavLink
      to={item.to}
      end={item.end}
      className={({ isActive }) =>
        `flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition ${
          isActive ? 'bg-brand-50 text-brand-800 dark:bg-brand-950 dark:text-brand-200' : 'text-ink-soft hover:bg-subtle'
        }`
      }
    >
      <item.icon className="size-4" aria-hidden />
      {item.label}
    </NavLink>
  )
}
