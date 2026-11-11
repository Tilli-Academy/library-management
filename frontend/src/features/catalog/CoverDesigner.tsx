import { useEffect, useRef, useState } from 'react'

import { Button } from '../../components/ui'
import { canvasToFile, COVER_STYLES, drawCover, type CoverStyle } from './coverArt'

/**
 * "Design a cover": pick a style, see a live poster with the book's title and author, then use it.
 * The result becomes a normal PNG file that goes through the same upload (and server checks) as a photo.
 */
export function CoverDesigner({
  title,
  author,
  onUse,
  onClose,
}: {
  title: string
  author: string
  onUse: (file: File) => void
  onClose: () => void
}) {
  const canvas = useRef<HTMLCanvasElement>(null)
  const [style, setStyle] = useState<CoverStyle>('dunes')
  const [fontsReady, setFontsReady] = useState(false)
  const [busy, setBusy] = useState(false)

  // Draw only once the classic font is loaded, otherwise the first poster uses a fallback font.
  useEffect(() => {
    let alive = true
    Promise.all([
      document.fonts.load("400 64px 'Instrument Serif'"),
    ])
      .catch(() => undefined)
      .finally(() => {
        if (alive) setFontsReady(true)
      })
    return () => {
      alive = false
    }
  }, [])

  useEffect(() => {
    if (fontsReady && canvas.current) drawCover(canvas.current, style, title, author)
  }, [fontsReady, style, title, author])

  async function use() {
    if (!canvas.current) return
    setBusy(true)
    try {
      onUse(await canvasToFile(canvas.current, `cover-${style}.png`))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="rounded-lg border border-line bg-surface p-3">
      <p className="mb-2 text-sm font-semibold text-ink">Design a cover</p>
      <div className="flex flex-col gap-4 sm:flex-row">
        <canvas
          ref={canvas}
          width={600}
          height={900}
          aria-label={`Cover preview: ${COVER_STYLES.find((s) => s.id === style)?.label}`}
          className="h-48 w-32 shrink-0 self-center rounded-md shadow-md ring-1 ring-black/10"
        />
        <div className="flex-1">
          <p className="mb-2 text-xs text-ink-soft">Choose a style. The title and author come from the form above.</p>
          <div className="flex flex-wrap gap-2" role="radiogroup" aria-label="Cover style">
            {COVER_STYLES.map((option) => (
              <button
                key={option.id}
                type="button"
                role="radio"
                aria-checked={style === option.id}
                onClick={() => setStyle(option.id)}
                className={`rounded-full px-3 py-1.5 text-xs font-semibold ring-1 transition ${
                  style === option.id ? 'bg-brand-700 text-white ring-brand-700' : 'bg-surface text-ink-soft ring-line hover:bg-subtle'
                }`}
              >
                {option.label}
              </button>
            ))}
          </div>
          <div className="mt-4 flex flex-wrap gap-2">
            <Button onClick={use} loading={busy} disabled={!fontsReady}>
              Use this cover
            </Button>
            <Button variant="ghost" onClick={onClose}>
              Close
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}
