import { useEffect, useMemo, useState } from 'react'
import { BookOpen, Plus, Search, Library, CheckCircle2, Clock3, ArrowDownToLine, Trash2, X, Sparkles } from 'lucide-react'
import { addBook, deleteBook, getBooks, issueBook, returnBook, type Book } from './api'

type Filter = 'all' | 'available' | 'issued'

export default function App() {
  const [books, setBooks] = useState<Book[]>([])
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState<Filter>('all')
  const [title, setTitle] = useState('')
  const [author, setAuthor] = useState('')
  const [modalOpen, setModalOpen] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [toast, setToast] = useState('')

  async function refresh() {
    try { setBooks(await getBooks()); setError('') }
    catch (e) { setError(e instanceof Error ? e.message : 'Could not connect to the server.') }
    finally { setLoading(false) }
  }
  useEffect(() => { void refresh() }, [])

  const stats = useMemo(() => ({
    total: books.length,
    available: books.filter(b => !b.issued).length,
    issued: books.filter(b => b.issued).length,
  }), [books])

  const visibleBooks = useMemo(() => books.filter(book => {
    const matchesQuery = `${book.title} ${book.author ?? ''}`.toLowerCase().includes(query.toLowerCase())
    const matchesFilter = filter === 'all' || (filter === 'issued' ? book.issued : !book.issued)
    return matchesQuery && matchesFilter
  }), [books, query, filter])

  function notify(message: string) {
    setToast(message)
    window.setTimeout(() => setToast(''), 2600)
  }

  async function handleAdd(e: React.FormEvent) {
    e.preventDefault()
    if (!title.trim()) return
    setSaving(true); setError('')
    try {
      await addBook(title.trim(), author.trim())
      setTitle(''); setAuthor(''); setModalOpen(false)
      await refresh(); notify('Book added to your library')
    } catch (e) { setError(e instanceof Error ? e.message : 'Unable to add book.') }
    finally { setSaving(false) }
  }

  async function handleAction(action: () => Promise<unknown>, message: string) {
    setError('')
    try { await action(); await refresh(); notify(message) }
    catch (e) { setError(e instanceof Error ? e.message : 'Action failed.') }
  }

  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand"><div className="brand-mark"><BookOpen size={21}/></div><span>PageTurn</span></div>
      <div className="side-label">WORKSPACE</div>
      <div className="nav-item active"><Library size={18}/> <span>My Library</span></div>
      <div className="sidebar-note"><Sparkles size={17}/><p>A little progress each day adds up to big results.</p></div>
      <div className="side-bottom"><div className="avatar">L</div><div><strong>Library Admin</strong><small>Workspace</small></div></div>
    </aside>

    <main className="main">
      <header className="topbar"><div className="breadcrumb">Workspace <span>/</span> <b>My Library</b></div><div className="top-status"><span className="live-dot"/> System online</div></header>
      <section className="welcome">
        <div><div className="eyebrow">YOUR PERSONAL COLLECTION</div><h1>Good things happen <em>between the pages.</em></h1><p>Keep your collection organised and every book within reach.</p></div>
        <button className="primary-btn" onClick={() => { setError(''); setModalOpen(true) }}><Plus size={18}/> Add a book</button>
      </section>

      {error && <div className="error-banner" role="alert">{error}<button onClick={() => setError('')} aria-label="Dismiss"><X size={16}/></button></div>}

      <section className="stats-grid">
        <Stat icon={<BookOpen/>} label="Total books" value={stats.total} tone="violet"/>
        <Stat icon={<CheckCircle2/>} label="Available now" value={stats.available} tone="green"/>
        <Stat icon={<ArrowDownToLine/>} label="Currently issued" value={stats.issued} tone="amber"/>
      </section>

      <section className="collection">
        <div className="section-heading"><div><h2>Your collection</h2><p>Browse, search and manage your books.</p></div><div className="count-chip">{stats.total} {stats.total === 1 ? 'BOOK' : 'BOOKS'}</div></div>
        <div className="toolbar">
          <div className="search-box"><Search size={18}/><input value={query} onChange={e => setQuery(e.target.value)} placeholder="Search by title or author..." aria-label="Search books"/></div>
          <div className="filters">{(['all','available','issued'] as Filter[]).map(f => <button key={f} className={filter === f ? 'filter active-filter' : 'filter'} onClick={() => setFilter(f)}>{f[0].toUpperCase()+f.slice(1)}</button>)}</div>
        </div>

        {loading ? <div className="empty-state"><div className="spinner"/><p>Loading your library...</p></div> :
        visibleBooks.length === 0 ? <div className="empty-state"><div className="empty-icon"><BookOpen size={25}/></div><h3>{books.length === 0 ? 'Your library starts here' : 'No books found'}</h3><p>{books.length === 0 ? 'Add your first book and start building your collection.' : 'Try another search or change the selected filter.'}</p>{books.length === 0 && <button className="primary-btn" onClick={() => setModalOpen(true)}><Plus size={17}/> Add your first book</button>}</div> :
        <div className="book-grid">{visibleBooks.map((book, index) => <article className="book-card" key={book.id}>
          <div className={`book-cover cover-${index % 5}`}><BookOpen size={27}/><span>PAGE<br/>TURN</span><small>BOOK No. {String(book.id).padStart(3,'0')}</small></div>
          <div className="book-info"><div className="book-topline"><span className={book.issued ? 'status issued' : 'status available'}><i/>{book.issued ? 'Issued' : 'Available'}</span><button className="icon-btn delete-btn" title="Delete book" disabled={book.issued} onClick={() => handleAction(() => deleteBook(book.id), 'Book removed')}><Trash2 size={16}/></button></div>
            <h3 title={book.title}>{book.title}</h3><p className="author">{book.author || 'Author not specified'}</p>
            <div className="card-footer">{book.issued ? <button className="secondary-btn" onClick={() => handleAction(() => returnBook(book.id), 'Book returned successfully')}><ArrowDownToLine size={15}/> Return book</button> : <button className="issue-btn" onClick={() => handleAction(() => issueBook(book.id), 'Book issued successfully')}><Clock3 size={15}/> Issue book</button>}</div>
          </div>
        </article>)}</div>}
      </section>
      <footer>Made for curious minds <span>✦</span> PageTurn Library Management</footer>
    </main>

    {modalOpen && <div className="modal-backdrop" onMouseDown={e => { if (e.target === e.currentTarget) setModalOpen(false) }}>
      <form className="modal" onSubmit={handleAdd}>
        <div className="modal-head"><div><div className="eyebrow">GROW YOUR COLLECTION</div><h2>Add a new book</h2></div><button type="button" className="icon-btn" onClick={() => setModalOpen(false)} aria-label="Close"><X/></button></div>
        <label>Book title <span>*</span><input autoFocus maxLength={160} value={title} onChange={e => setTitle(e.target.value)} placeholder="e.g. Atomic Habits" required/></label>
        <label>Author <small>(optional)</small><input maxLength={120} value={author} onChange={e => setAuthor(e.target.value)} placeholder="e.g. James Clear"/></label>
        <div className="modal-actions"><button type="button" className="cancel-btn" onClick={() => setModalOpen(false)}>Cancel</button><button className="primary-btn" disabled={saving || !title.trim()}>{saving ? 'Adding...' : <><Plus size={17}/> Add book</>}</button></div>
      </form>
    </div>}
    {toast && <div className="toast"><CheckCircle2 size={18}/>{toast}</div>}
  </div>
}

function Stat({icon,label,value,tone}:{icon:React.ReactNode;label:string;value:number;tone:string}) {
  return <div className="stat-card"><div className={`stat-icon ${tone}`}>{icon}</div><div><p>{label}</p><strong>{value}</strong></div></div>
}
