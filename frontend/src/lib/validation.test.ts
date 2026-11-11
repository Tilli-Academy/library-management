import { describe, expect, it } from 'vitest'

import { dueLabel, expectedBackLabel } from './format'
import { safeRedirect } from './redirect'
import { bookSchema, phoneSchema, registerSchema } from './schemas'

describe('safeRedirect', () => {
  it('allows paths inside the app', () => {
    expect(safeRedirect('/my-books')).toBe('/my-books')
    expect(safeRedirect('/admin/users?q=x')).toBe('/admin/users?q=x')
  })

  it.each(['https://evil.example', '//evil.example', '/\\evil.example', 'javascript:alert(1)', '', null, 42])(
    'blocks %s',
    (value) => {
      expect(safeRedirect(value)).toBe('/')
    },
  )
})

describe('form rules', () => {
  const validBook = { title: 'Dune', author: 'Frank Herbert', isbn: '', genre: '', description: '', publishedYear: '', totalCopies: '2' }

  it('accepts a normal book and trims spaces', () => {
    const result = bookSchema.parse({ ...validBook, title: '  Dune  ' })
    expect(result.title).toBe('Dune')
  })

  it('refuses invisible control characters', () => {
    expect(bookSchema.safeParse({ ...validBook, title: 'Bad\u0000title' }).success).toBe(false)
    expect(bookSchema.safeParse({ ...validBook, description: 'Line one\nLine two' }).success).toBe(true)
  })

  it('keeps HTML as harmless text (React escapes it when showing it)', () => {
    expect(bookSchema.parse({ ...validBook, title: '<script>alert(1)</script>' }).title).toBe('<script>alert(1)</script>')
  })

  it('enforces copies and year ranges', () => {
    expect(bookSchema.safeParse({ ...validBook, totalCopies: '0' }).success).toBe(false)
    expect(bookSchema.safeParse({ ...validBook, totalCopies: '1001' }).success).toBe(false)
    expect(bookSchema.safeParse({ ...validBook, publishedYear: '99' }).success).toBe(false)
  })

  it('requires a strong, confirmed password', () => {
    const base = { displayName: 'Ada', phone: '+91 98765 43210', email: 'ada@example.com', password: 'correct-horse-battery', confirmPassword: 'correct-horse-battery' }
    expect(registerSchema.safeParse(base).success).toBe(true)
    expect(registerSchema.safeParse({ ...base, password: 'short', confirmPassword: 'short' }).success).toBe(false)
    expect(registerSchema.safeParse({ ...base, confirmPassword: 'different-password' }).success).toBe(false)
    expect(registerSchema.safeParse({ ...base, email: 'not-an-email' }).success).toBe(false)
  })

  it('needs a real-looking phone number', () => {
    expect(phoneSchema.safeParse('020 (7946) 0958').success).toBe(true)
    expect(phoneSchema.safeParse('').success).toBe(false)
    expect(phoneSchema.safeParse('call me').success).toBe(false)
    expect(phoneSchema.safeParse('12').success).toBe(false)
  })
})

describe('dueLabel', () => {
  const now = new Date(2026, 0, 10, 12, 0, 0)
  it('describes due dates in plain words', () => {
    expect(dueLabel(new Date(2026, 0, 10, 18, 0).toISOString(), now)).toBe('Due today')
    expect(dueLabel(new Date(2026, 0, 11, 9, 0).toISOString(), now)).toBe('Due tomorrow')
    expect(dueLabel(new Date(2026, 0, 15, 9, 0).toISOString(), now)).toBe('Due in 5 days')
    expect(dueLabel(new Date(2026, 0, 8, 9, 0).toISOString(), now)).toBe('2 days overdue')
  })
})

describe('expectedBackLabel', () => {
  const now = new Date(2026, 0, 10, 12, 0, 0)
  it('tells members when a book should be back', () => {
    expect(expectedBackLabel(new Date(2026, 0, 13, 9, 0).toISOString(), now)).toMatch(/^Expected back in 3 days \(/)
    expect(expectedBackLabel(new Date(2026, 0, 11, 9, 0).toISOString(), now)).toBe('Expected back tomorrow')
    expect(expectedBackLabel(new Date(2026, 0, 10, 18, 0).toISOString(), now)).toBe('Expected back today')
    expect(expectedBackLabel(new Date(2026, 0, 5, 9, 0).toISOString(), now)).toBe('Expected back any day now')
    expect(expectedBackLabel(null, now)).toBe('All copies on loan')
  })
})
