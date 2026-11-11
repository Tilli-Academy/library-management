import { Link } from 'react-router'

import { EmptyState } from './ui'

export function NotFound() {
  return (
    <EmptyState title="Page not found">
      <Link to="/" className="font-semibold text-brand-700 hover:underline dark:text-brand-300">
        Back to the catalogue
      </Link>
    </EmptyState>
  )
}
