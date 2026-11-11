import { BookMarked, LayoutDashboard, Library, Lightbulb, LogOut, Repeat, Users } from 'lucide-react'
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
      {/* Side menu: dark wood panel, like library shelving. */}
      <aside className="sticky top-0 hidden h-dvh w-64 shrink-0 flex-col bg-wood px-4 py-6 text-cream md:flex">
        <Wordmark className="mb-8 px-2 text-3xl" />
        <nav className="flex flex-1 flex-col gap-1" aria-label="Main">
          {memberNav.map((item) => (
            <SideLink key={item.to} item={item} />
          ))}
          {isLibrarian && (
            <>
              <p className="mt-6 mb-1 px-3 font-display text-sm tracking-[0.2em] text-gold uppercase">Librarian</p>
              {librarianNav.map((item) => (
                <SideLink key={item.to} item={item} />
              ))}
            </>
          )}
        </nav>
        <div className="mt-6 border-t border-cream/15 pt-4">
          <div className="flex items-center gap-3 px-2">
            <div className="grid size-9 shrink-0 place-items-center rounded-full border border-gold/60 font-display text-lg font-semibold text-gold">
              {user?.displayName.charAt(0).toUpperCase()}
            </div>
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold">{user?.displayName}</p>
              <p className="truncate text-xs text-cream/60">{isLibrarian ? 'Librarian' : 'Member'}</p>
            </div>
          </div>
          <button
            type="button"
            onClick={handleSignOut}
            className="mt-3 flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm text-cream/75 hover:bg-cream/10 hover:text-cream"
          >
            <LogOut className="size-4" aria-hidden /> Sign out
          </button>
        </div>
      </aside>

      <header className="sticky top-0 z-30 flex items-center justify-between bg-wood px-4 py-3 text-cream shadow-md md:hidden">
        <Wordmark className="text-2xl" />
        <button type="button" onClick={handleSignOut} className="rounded-lg p-2 text-cream/80 hover:bg-cream/10" aria-label="Sign out">
          <LogOut className="size-5" aria-hidden />
        </button>
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 pt-6 pb-28 sm:px-6 md:px-10 md:pt-10 md:pb-10">
        <Outlet />
      </main>

      <nav
        className="fixed inset-x-0 bottom-0 z-30 flex bg-wood pb-[env(safe-area-inset-bottom)] text-cream shadow-[0_-4px_12px_rgba(0,0,0,0.25)] md:hidden"
        aria-label="Main"
      >
        {items.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) =>
              `flex flex-1 flex-col items-center gap-0.5 py-2 text-[11px] font-medium ${isActive ? 'text-gold' : 'text-cream/60'}`
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

/** "PageTurn LIBRARY" in the welcome page's lettering. */
function Wordmark({ className = '' }: { className?: string }) {
  return (
    <p className={`font-display leading-none text-cream ${className}`}>
      <span className="font-bold">PageTurn</span>{' '}
      <span className="text-[0.5em] font-medium tracking-[0.25em] text-gold uppercase">Library</span>
    </p>
  )
}

function SideLink({ item }: { item: NavItem }) {
  return (
    <NavLink
      to={item.to}
      end={item.end}
      className={({ isActive }) =>
        `flex items-center gap-3 rounded-lg border-l-2 px-3 py-2 text-sm font-medium transition ${
          isActive ? 'border-gold bg-cream/10 text-cream' : 'border-transparent text-cream/70 hover:bg-cream/5 hover:text-cream'
        }`
      }
    >
      <item.icon className="size-4" aria-hidden />
      {item.label}
    </NavLink>
  )
}
