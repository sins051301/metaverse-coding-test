import { useSyncExternalStore } from 'react'

let nicknames: readonly string[] = []
const listeners = new Set<() => void>()

export const pairRequestStore = {
  set(next: readonly string[]) {
    nicknames = next
    listeners.forEach((listener) => listener())
  },
  subscribe(listener: () => void) {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  },
}

export function usePairRequestNicknames() {
  return useSyncExternalStore(pairRequestStore.subscribe, () => nicknames)
}
