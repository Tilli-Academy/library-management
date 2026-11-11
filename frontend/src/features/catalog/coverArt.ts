/**
 * Draws poster-style book covers on a <canvas>: a scene (desert, night sky, mountains, sea, city, leather)
 * with the title and author in the classic serif. Everything is drawn locally in the browser; the result
 * is uploaded like any other cover picture (the server checks and re-encodes it).
 */

export const COVER_WIDTH = 600
export const COVER_HEIGHT = 900

export type CoverStyle = 'dunes' | 'stars' | 'mountains' | 'ocean' | 'city' | 'leather'

export const COVER_STYLES: { id: CoverStyle; label: string }[] = [
  { id: 'dunes', label: 'Desert dunes' },
  { id: 'stars', label: 'Starry night' },
  { id: 'mountains', label: 'Misty mountains' },
  { id: 'ocean', label: 'Ocean waves' },
  { id: 'city', label: 'City at night' },
  { id: 'leather', label: 'Classic leather' },
]

const SERIF = "'Inter Variable', system-ui, sans-serif"

/** Same title => same "random" details, so a book's poster doesn't change every time it's drawn. */
export function seededRandom(seed: string): () => number {
  let h = 2166136261
  for (const char of seed) {
    h ^= char.charCodeAt(0)
    h = Math.imul(h, 16777619)
  }
  return () => {
    h += 0x6d2b79f5
    let t = h
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

/**
 * Splits text into lines that fit `maxWidth`, shrinking the font until it fits in `maxLines`.
 * `measure(text, size)` returns the width of the text at that font size.
 */
export function fitText(
  text: string,
  maxWidth: number,
  maxLines: number,
  startSize: number,
  minSize: number,
  measure: (text: string, size: number) => number,
): { lines: string[]; size: number } {
  const words = text.trim().split(/\s+/).filter(Boolean)
  if (words.length === 0) return { lines: [], size: startSize }
  for (let size = startSize; size >= minSize; size -= 4) {
    const lines: string[] = []
    let current = ''
    let fits = true
    for (const word of words) {
      const candidate = current ? `${current} ${word}` : word
      if (measure(candidate, size) <= maxWidth) {
        current = candidate
      } else {
        if (current) lines.push(current)
        if (measure(word, size) > maxWidth) fits = false // a single word is too long at this size
        current = word
      }
    }
    if (current) lines.push(current)
    if (fits && lines.length <= maxLines) return { lines, size }
  }
  // Still too long at the smallest size: keep the first lines and end with an ellipsis.
  const size = minSize
  const lines: string[] = []
  let current = ''
  for (const word of words) {
    const candidate = current ? `${current} ${word}` : word
    if (measure(candidate, size) <= maxWidth) current = candidate
    else {
      if (current) lines.push(current)
      current = word
    }
  }
  if (current) lines.push(current)
  if (lines.length <= maxLines) return { lines, size }
  const kept = lines.slice(0, maxLines)
  let last = `${kept[maxLines - 1]}…`
  while (measure(last, size) > maxWidth && last.includes(' ')) last = last.replace(/\s+\S+…$/, '…')
  kept[maxLines - 1] = last
  return { lines: kept, size }
}

interface Palette {
  sky: [string, string]
  text: string
  sub: string
  /** The lettering must end above this line (the scenery starts below it). */
  textLimit: number
  /** Lowest position for the sun/moon (below this it would sink completely into the landscape). */
  lightLimit: number
}

const PALETTES: Record<CoverStyle, Palette> = {
  dunes: { sky: ['#f6b26b', '#c8653a'], text: '#2b1408', sub: '#3a1c0d', textLimit: 470, lightLimit: 590 },
  stars: { sky: ['#0b1026', '#2b2357'], text: '#f3eee3', sub: '#ddd0ff', textLimit: 560, lightLimit: 650 },
  mountains: { sky: ['#dfe7e6', '#8fa6a6'], text: '#1d2b2b', sub: '#22393a', textLimit: 410, lightLimit: 480 },
  ocean: { sky: ['#bfe3f0', '#3f8fb5'], text: '#0d2d3d', sub: '#174a63', textLimit: 470, lightLimit: 540 },
  city: { sky: ['#1a0f2e', '#5a2a4f'], text: '#ffe7b0', sub: '#f2b8a0', textLimit: 440, lightLimit: 500 },
  leather: { sky: ['#5c1f1f', '#2b0e0e'], text: '#e2c27a', sub: '#c9a45c', textLimit: 620, lightLimit: 620 },
}

function sky(ctx: CanvasRenderingContext2D, [top, bottom]: [string, string]) {
  const g = ctx.createLinearGradient(0, 0, 0, COVER_HEIGHT)
  g.addColorStop(0, top)
  g.addColorStop(1, bottom)
  ctx.fillStyle = g
  ctx.fillRect(0, 0, COVER_WIDTH, COVER_HEIGHT)
}

function wave(ctx: CanvasRenderingContext2D, base: number, amp: number, freq: number, phase: number, color: string) {
  ctx.beginPath()
  ctx.moveTo(0, COVER_HEIGHT)
  for (let x = 0; x <= COVER_WIDTH; x += 6) ctx.lineTo(x, base + amp * Math.sin(x * freq + phase))
  ctx.lineTo(COVER_WIDTH, COVER_HEIGHT)
  ctx.closePath()
  ctx.fillStyle = color
  ctx.fill()
}

function disc(ctx: CanvasRenderingContext2D, x: number, y: number, r: number, color: string) {
  ctx.beginPath()
  ctx.arc(x, y, r, 0, Math.PI * 2)
  ctx.fillStyle = color
  ctx.fill()
}

/** A crescent moon as one shape (outer circle minus an offset inner circle). */
function crescent(ctx: CanvasRenderingContext2D, x: number, y: number, r: number, color: string) {
  ctx.beginPath()
  ctx.arc(x, y, r, 0, Math.PI * 2)
  ctx.arc(x + r * 0.42, y - r * 0.28, r * 0.88, 0, Math.PI * 2, true)
  ctx.fillStyle = color
  ctx.fill('evenodd')
}

/**
 * Draws the scene. `lightY` is where the sun/moon goes: always below the lettering, so it never covers
 * the title. The landscape is drawn after it, so a low sun sets "behind" the dunes, hills or buildings.
 */
function drawScene(ctx: CanvasRenderingContext2D, style: CoverStyle, rnd: () => number, lightY: number) {
  const p = PALETTES[style]
  sky(ctx, p.sky)
  switch (style) {
    case 'dunes': {
      disc(ctx, 380 + rnd() * 120, lightY, 55, '#ffe9b8')
      wave(ctx, 600, 45, 0.006, rnd() * 6, '#d98c4f')
      wave(ctx, 690, 60, 0.005, rnd() * 6, '#bf7240')
      wave(ctx, 790, 50, 0.007, rnd() * 6, '#9e5a30')
      break
    }
    case 'stars': {
      for (let i = 0; i < 160; i++) disc(ctx, rnd() * COVER_WIDTH, rnd() * 700, rnd() * 1.8 + 0.3, `rgba(255,255,255,${0.4 + rnd() * 0.6})`)
      crescent(ctx, 400 + rnd() * 90, lightY, 50, '#f6f0d8')
      wave(ctx, 760, 30, 0.012, rnd() * 6, '#141033')
      break
    }
    case 'mountains': {
      disc(ctx, 150 + rnd() * 300, lightY, 45, 'rgba(255,255,255,0.75)')
      const layers = ['#a9bcbb', '#7f9696', '#566e6e']
      layers.forEach((color, i) => {
        ctx.beginPath()
        ctx.moveTo(0, COVER_HEIGHT)
        let x = 0
        const base = 640 + i * 85
        ctx.lineTo(0, base)
        while (x < COVER_WIDTH) {
          const peak = base - 50 - rnd() * 110
          x += 90 + rnd() * 80
          ctx.lineTo(x - 50, peak)
          ctx.lineTo(x, base - rnd() * 20)
        }
        ctx.lineTo(COVER_WIDTH, COVER_HEIGHT)
        ctx.closePath()
        ctx.fillStyle = color
        ctx.fill()
      })
      break
    }
    case 'ocean': {
      disc(ctx, 120 + rnd() * 360, lightY, 60, 'rgba(255,248,220,0.9)')
      ;['#5aa7c9', '#3c88ad', '#256b8f', '#174f6d'].forEach((color, i) => wave(ctx, 560 + i * 80, 14 + i * 4, 0.02 + i * 0.004, rnd() * 6, color))
      break
    }
    case 'city': {
      for (let i = 0; i < 70; i++) disc(ctx, rnd() * COVER_WIDTH, rnd() * 450, rnd() * 1.4 + 0.3, 'rgba(255,255,255,0.7)')
      disc(ctx, 420 + rnd() * 100, lightY, 40, '#ffe7b0')
      let x = -10
      while (x < COVER_WIDTH) {
        const w = 40 + rnd() * 60
        const h = 150 + rnd() * 220
        ctx.fillStyle = '#120a1f'
        ctx.fillRect(x, COVER_HEIGHT - h, w, h)
        for (let wy = COVER_HEIGHT - h + 14; wy < COVER_HEIGHT - 20; wy += 22) {
          for (let wx = x + 8; wx < x + w - 10; wx += 16) {
            if (rnd() > 0.55) {
              ctx.fillStyle = 'rgba(255,214,140,0.85)'
              ctx.fillRect(wx, wy, 7, 10)
            }
          }
        }
        x += w + 4
      }
      break
    }
    case 'leather': {
      ctx.strokeStyle = p.sub
      ctx.lineWidth = 3
      ctx.strokeRect(36, 36, COVER_WIDTH - 72, COVER_HEIGHT - 72)
      ctx.lineWidth = 1
      ctx.strokeRect(50, 50, COVER_WIDTH - 100, COVER_HEIGHT - 100)
      // small ornament
      ctx.beginPath()
      ctx.moveTo(COVER_WIDTH / 2 - 60, 640)
      ctx.lineTo(COVER_WIDTH / 2 + 60, 640)
      ctx.stroke()
      disc(ctx, COVER_WIDTH / 2, 640, 6, p.sub)
      break
    }
  }
}

/** Draws the complete poster (scene + title + author) onto a 600 × 900 canvas. */
export function drawCover(canvas: HTMLCanvasElement, style: CoverStyle, title: string, author: string) {
  canvas.width = COVER_WIDTH
  canvas.height = COVER_HEIGHT
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  const rnd = seededRandom(`${style}|${title}|${author}`)
  const p = PALETTES[style]

  // 1. Work out the lettering first. Long titles shrink until the text ends above the scenery.
  const titleFont = (size: number) => `700 ${size}px ${SERIF}`
  const authorFont = (size: number) => `500 ${size}px ${SERIF}`
  const measureWith = (font: (size: number) => string) => (text: string, size: number) => {
    ctx.font = font(size)
    return ctx.measureText(text).width
  }
  const top = style === 'leather' ? 230 : 120
  const layout = (maxTitleSize: number) => {
    const heading = fitText((title || 'Untitled').toUpperCase(), 500, 4, maxTitleSize, 34, measureWith(titleFont))
    const byline = author.trim()
      ? fitText(author.toUpperCase(), 480, 2, Math.min(40, Math.round(maxTitleSize * 0.42)), 20, measureWith(authorFont))
      : { lines: [] as string[], size: 0 }
    const lineHeight = heading.size * 1.05
    const authorTop = top + heading.size + (heading.lines.length - 1) * lineHeight + byline.size + 22
    const bottom = byline.lines.length ? authorTop + (byline.lines.length - 1) * byline.size * 1.2 : authorTop - byline.size - 22
    return { heading, byline, lineHeight, authorTop, bottom }
  }
  let text = layout(96)
  for (let max = 90; text.bottom > p.textLimit && max >= 34; max -= 6) text = layout(max)

  // 2. The scene, with the sun/moon below the text (but not sunk completely into the landscape).
  drawScene(ctx, style, rnd, Math.min(text.bottom + 95, p.lightLimit))

  // 3. The lettering on top. The last fillText argument squeezes a still-too-wide line so it always fits.
  ctx.textAlign = 'center'
  ctx.textBaseline = 'alphabetic'
  ctx.fillStyle = p.text
  ctx.font = titleFont(text.heading.size)
  text.heading.lines.forEach((line, i) => ctx.fillText(line, COVER_WIDTH / 2, top + text.heading.size + i * text.lineHeight, 520))
  ctx.fillStyle = p.sub
  ctx.font = authorFont(text.byline.size)
  text.byline.lines.forEach((line, i) => ctx.fillText(line, COVER_WIDTH / 2, text.authorTop + i * text.byline.size * 1.2, 520))
}

/** The canvas as a PNG file, ready to upload as the cover. */
export function canvasToFile(canvas: HTMLCanvasElement, name: string): Promise<File> {
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => (blob ? resolve(new File([blob], name, { type: 'image/png' })) : reject(new Error('Could not create the picture.'))), 'image/png')
  })
}
