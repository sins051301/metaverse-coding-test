import type { Activity } from '@/entities/lounge/api/loungeMessages'
import { loungeStore } from '@/entities/lounge/model/loungeStore'

const TYPING_HOLD_MS = 5_000
const NAP_AFTER_MS = 5 * 60_000

let activity: Activity = 'idle'
let typingTimer: ReturnType<typeof setTimeout> | undefined
let napTimer: ReturnType<typeof setTimeout> | undefined
const listeners = new Set<(activity: Activity) => void>()

function set(next: Activity) {
  if (next === activity) {
    return
  }
  activity = next
  listeners.forEach((listener) => listener(activity))
}

function restartNapTimer() {
  clearTimeout(napTimer)
  if (loungeStore.getState().seatedDeskId !== null) {
    napTimer = setTimeout(() => set('napping'), NAP_AFTER_MS)
  }
}

let seatedDeskId = loungeStore.getState().seatedDeskId
loungeStore.subscribe(() => {
  const next = loungeStore.getState().seatedDeskId
  if (next === seatedDeskId) {
    return
  }
  seatedDeskId = next
  clearTimeout(typingTimer)
  set('idle')
  restartNapTimer()
})
restartNapTimer()

export const activityTracker = {
  get: () => activity,
  touch() {
    if (loungeStore.getState().seatedDeskId === null) {
      return
    }
    set('typing')
    clearTimeout(typingTimer)
    typingTimer = setTimeout(() => set('idle'), TYPING_HOLD_MS)
    restartNapTimer()
  },
  subscribe(listener: (activity: Activity) => void) {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  },
}
