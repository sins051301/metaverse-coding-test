import { useSyncExternalStore } from 'react'
import { readStorage, removeStorage, writeStorage } from '@/shared/lib/storage'

export type NearbyDesk = {
  owner: { avatarId: string; nickname: string } | null
  deskId: number
  screenX: number
  screenY: number
}

export type LoungeView = 'lounge' | 'panel'

export type LoungeState = {
  view: LoungeView
  nearbyDesk: NearbyDesk | null
  spectateRequest: string | null
  seatedDeskId: number | null
  paused: boolean
  failedAt: number | null
}

const SEATED_DESK_KEY = 'codingtest:v1:seatedDesk'

function readSeatedDesk(): number | null {
  const saved = readStorage(sessionStorage, SEATED_DESK_KEY)
  return saved === null ? null : Number(saved)
}

let state: LoungeState = {
  view: 'lounge',
  nearbyDesk: null,
  spectateRequest: null,
  seatedDeskId: readSeatedDesk(),
  paused: false,
  failedAt: null,
}
const listeners = new Set<() => void>()

function update(next: Partial<LoungeState>) {
  state = { ...state, ...next }
  listeners.forEach((listener) => listener())
}

export const loungeStore = {
  getState: () => state,
  subscribe(listener: () => void) {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  },
  setNearbyDesk(nearbyDesk: NearbyDesk | null) {
    if (
      nearbyDesk?.deskId === state.nearbyDesk?.deskId &&
      nearbyDesk?.owner?.avatarId === state.nearbyDesk?.owner?.avatarId &&
      nearbyDesk?.screenX === state.nearbyDesk?.screenX &&
      nearbyDesk?.screenY === state.nearbyDesk?.screenY
    ) {
      return
    }
    update({ nearbyDesk })
  },
  sit(deskId: number) {
    writeStorage(sessionStorage, SEATED_DESK_KEY, String(deskId))
    update({ seatedDeskId: deskId, nearbyDesk: null })
  },
  stand() {
    removeStorage(sessionStorage, SEATED_DESK_KEY)
    update({ seatedDeskId: null })
  },
  setView(view: LoungeView) {
    if (view !== state.view) {
      update({ view, nearbyDesk: null })
    }
  },
  flashFailure() {
    update({ failedAt: performance.now() })
  },
  requestSpectate(ownerAvatarId: string | null) {
    update({ spectateRequest: ownerAvatarId })
  },
  setPaused(paused: boolean) {
    if (paused !== state.paused) {
      update({ paused })
    }
  },
}

export function useLoungeState(): LoungeState {
  return useSyncExternalStore(loungeStore.subscribe, loungeStore.getState)
}
