import type { ReactNode } from 'react'

import libraryHall from '../assets/library-hall.jpg'
import libraryHallSmall from '../assets/library-hall-small.jpg'

/**
 * Full-screen photo of a historic library hall with a dark veil, used behind the welcome and sign-in pages.
 *
 * Photo: Strahov Library, Theological Hall (Prague) by Jorge Royan, CC BY-SA 3.0, via Wikimedia Commons —
 * resized and darkened. The licence requires this credit, which is shown at the bottom of the page.
 */
export function LibraryBackdrop({ children }: { children: ReactNode }) {
  return (
    <div className="relative min-h-dvh overflow-hidden bg-stone-950 text-[#f3eee3]">
      <img
        src={libraryHall}
        srcSet={`${libraryHallSmall} 1000w, ${libraryHall} 1920w`}
        sizes="100vw"
        alt=""
        fetchPriority="high"
        className="absolute inset-0 size-full object-cover"
      />
      <div className="absolute inset-0 bg-gradient-to-b from-black/60 via-black/45 to-black/75" aria-hidden />
      <div className="relative flex min-h-dvh flex-col">{children}</div>
      <p className="absolute right-3 bottom-2 left-3 text-right text-[10px] text-[#f3eee3]/60">
        Photo: Strahov Library, Prague — Jorge Royan,{' '}
        <a
          href="https://commons.wikimedia.org/wiki/File:Strahov_Theological_Hall,_Prague_-_7565.jpg"
          target="_blank"
          rel="noopener noreferrer"
          className="underline hover:text-[#f3eee3]"
        >
          CC BY-SA 3.0
        </a>
      </p>
    </div>
  )
}
