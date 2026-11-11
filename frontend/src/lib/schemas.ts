import { z } from 'zod'

/**
 * Form rules — the same limits the server enforces. Checking in the browser gives instant feedback;
 * the server checks everything again, because browser checks can always be bypassed.
 */

// Invisible control characters (NUL, escape codes, ...) are refused. Line breaks only where noted.
// eslint-disable-next-line no-control-regex
const SINGLE_LINE = /^[^\u0000-\u001F\u007F]*$/
// eslint-disable-next-line no-control-regex
const MULTI_LINE = /^[^\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]*$/

const text = (label: string, max: number) =>
  z
    .string()
    .trim()
    .max(max, `${label} must be at most ${max} characters.`)
    .regex(SINGLE_LINE, `${label} contains invalid characters.`)

const passwordBytes = (value: string) => new TextEncoder().encode(value).length <= 72

export const loginSchema = z.object({
  email: z.string().trim().min(1, 'Email is required.').max(320, 'Email is too long.'),
  password: z.string().min(1, 'Password is required.').max(128, 'Password is too long.'),
})
export type LoginForm = z.infer<typeof loginSchema>

export const registerSchema = z
  .object({
    displayName: text('Name', 100).min(1, 'Your name is required.'),
    email: z.string().trim().min(1, 'Email is required.').max(320, 'Email is too long.').pipe(z.email('Enter a valid email address.')),
    password: z
      .string()
      .min(12, 'Password must be at least 12 characters.')
      .max(72, 'Password must be at most 72 characters.')
      .refine(passwordBytes, 'Password is too long (accented letters and emoji count double).'),
    confirmPassword: z.string(),
  })
  .refine((form) => form.password === form.confirmPassword, {
    path: ['confirmPassword'],
    message: 'Passwords do not match.',
  })
export type RegisterForm = z.infer<typeof registerSchema>

const optionalYear = z
  .string()
  .trim()
  .refine((value) => value === '' || /^\d{4}$/.test(value), 'Enter a 4-digit year.')
  .refine((value) => value === '' || (Number(value) >= 1000 && Number(value) <= 2100), 'Enter a valid year.')

export const bookSchema = z.object({
  title: text('Title', 200).min(1, 'Title is required.'),
  author: text('Author', 150).min(1, 'Author is required.'),
  isbn: z
    .string()
    .trim()
    .max(17, 'ISBN is too long.')
    .regex(/^[0-9Xx\- ]*$/, 'ISBN may only contain digits, X, spaces and dashes.'),
  genre: text('Genre', 60),
  description: z
    .string()
    .trim()
    .max(2000, 'Description must be at most 2000 characters.')
    .regex(MULTI_LINE, 'Description contains invalid characters.'),
  publishedYear: optionalYear,
  totalCopies: z
    .string()
    .trim()
    .regex(/^\d{1,4}$/, 'Enter a whole number.')
    .refine((value) => Number(value) >= 1 && Number(value) <= 1000, 'Between 1 and 1000 copies.'),
})
export type BookForm = z.infer<typeof bookSchema>

export const issueSchema = z.object({
  memberEmail: z.string().trim().min(1, 'Member email is required.').max(320).pipe(z.email('Enter a valid email address.')),
})
export type IssueForm = z.infer<typeof issueSchema>
