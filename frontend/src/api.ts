export type Book = { id: number; title: string; author: string; issued: boolean }
const API = 'http://localhost:8080/api/books'

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...options?.headers },
  })
  if (!response.ok) {
    let message = 'Something went wrong. Please try again.'
    try { const body = await response.json(); message = body.message || body.detail || message } catch {}
    throw new Error(message)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}
export const getBooks = () => request<Book[]>(API)
export const addBook = (title: string, author: string) =>
  request<Book>(API, { method: 'POST', body: JSON.stringify({ title, author }) })
export const issueBook = (id: number) =>
  request<Book>(`${API}/${id}/issue`, { method: 'PATCH' })
export const returnBook = (id: number) =>
  request<Book>(`${API}/${id}/return`, { method: 'PATCH' })
export const deleteBook = (id: number) =>
  request<void>(`${API}/${id}`, { method: 'DELETE' })
