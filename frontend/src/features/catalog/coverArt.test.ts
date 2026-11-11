import { describe, expect, it } from 'vitest'

import { fitText, seededRandom } from './coverArt'

// Pretend every character is 0.5 × font size wide.
const measure = (text: string, size: number) => text.length * size * 0.5

describe('cover lettering', () => {
  it('keeps short titles big on one line', () => {
    expect(fitText('DUNE', 500, 4, 96, 40, measure)).toEqual({ lines: ['DUNE'], size: 96 })
  })

  it('wraps longer titles and shrinks them until they fit', () => {
    const result = fitText('THE THURSDAY MURDER CLUB', 500, 4, 96, 40, measure)
    expect(result.lines.length).toBeLessThanOrEqual(4)
    for (const line of result.lines) expect(measure(line, result.size)).toBeLessThanOrEqual(500)
    expect(result.lines.join(' ')).toBe('THE THURSDAY MURDER CLUB')
  })

  it('ends very long titles with an ellipsis instead of running off the cover', () => {
    const long = Array.from({ length: 40 }, (_, i) => `WORD${i}`).join(' ')
    const result = fitText(long, 500, 4, 96, 40, measure)
    expect(result.lines).toHaveLength(4)
    expect(result.lines[3].endsWith('…')).toBe(true)
  })

  it('gives the same details for the same book every time', () => {
    const a = seededRandom('dunes|Dune|Frank Herbert')
    const b = seededRandom('dunes|Dune|Frank Herbert')
    const c = seededRandom('dunes|Emma|Jane Austen')
    const first = [a(), a(), a()]
    expect([b(), b(), b()]).toEqual(first)
    expect([c(), c(), c()]).not.toEqual(first)
    first.forEach((n) => expect(n).toBeGreaterThanOrEqual(0))
  })
})
