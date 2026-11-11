import { useSyncExternalStore } from 'react'

const TICK_MS = 30_000

let now = Date.now()
const listeners = new Set<() => void>()
let timer: ReturnType<typeof setInterval> | undefined

function subscribe(listener: () => void) {
  listeners.add(listener)
  if (timer === undefined) {
    timer = setInterval(() => {
      now = Date.now()
      listeners.forEach((notify) => notify())
    }, TICK_MS)
  }
  return () => {
    listeners.delete(listener)
    if (listeners.size === 0) {
      clearInterval(timer)
      timer = undefined
    }
  }
}

export function useNow(): number {
  return useSyncExternalStore(subscribe, () => now)
}
