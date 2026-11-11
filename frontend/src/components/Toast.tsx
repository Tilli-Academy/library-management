import { CheckCircle2 } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'

/** Small confirmation message at the bottom of the screen. */
export function useToast() {
  const [message, setMessage] = useState<string | null>(null)
  useEffect(() => {
    if (!message) return
    const timer = window.setTimeout(() => setMessage(null), 3000)
    return () => window.clearTimeout(timer)
  }, [message])
  const toast = message ? (
    <div
      role="status"
      className="fixed inset-x-4 bottom-24 z-50 mx-auto flex max-w-sm items-center gap-2 rounded-xl bg-ink px-4 py-3 text-sm text-page shadow-lg md:bottom-6"
    >
      <CheckCircle2 className="size-4 shrink-0" aria-hidden />
      {message}
    </div>
  ) : null
  return { toast, show: useCallback((text: string) => setMessage(text), []) }
}
