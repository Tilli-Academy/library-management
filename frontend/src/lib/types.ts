export type Role = 'MEMBER' | 'LIBRARIAN'

export interface User {
  id: string
  email: string
  displayName: string
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
}

export interface Loan {
  id: number
  bookId: number
  bookTitle: string
  bookAuthor: string
  memberId: string
  memberName: string
  memberEmail: string
  issuedAt: string
  dueAt: string
  returnedAt: string | null
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
