export type Role = 'MEMBER' | 'LIBRARIAN'

export interface User {
  id: string
  email: string
  displayName: string
  phone: string | null
  role: Role
  enabled: boolean
  createdAt: string
}

export interface Book {
  id: number
  isbn: string | null
  title: string
  author: string
  genre: string | null
  description: string | null
  publishedYear: number | null
  totalCopies: number
  availableCopies: number
  /** Only when no copy is on the shelf: when the first borrowed copy is due back. */
  expectedBackAt: string | null
}

export interface Loan {
  id: number
  bookId: number
  bookTitle: string
  bookAuthor: string
  memberId: string
  memberName: string
  memberEmail: string
  memberPhone: string | null
  issuedAt: string
  dueAt: string
  returnedAt: string | null
  lastRemindedAt: string | null
  overdue: boolean
}

export interface LibraryStats {
  titles: number
  totalCopies: number
  availableCopies: number
  activeLoans: number
  overdueLoans: number
  members: number
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export type LoanStatus = 'ACTIVE' | 'OVERDUE' | 'RETURNED'
