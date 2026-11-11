import { useSyncExternalStore } from 'react'
import type {
  AvatarState,
  RecentSolve,
} from '@/entities/lounge/api/loungeMessages'

type RosterState = {
  connected: boolean
  myAvatarId: string | null
  avatars: ReadonlyMap<string, AvatarState>
  recentSolves: readonly RecentSolve[]
  solvedAt: ReadonlyMap<string, number>
}

let state: RosterState = {
  connected: false,
  myAvatarId: null,
  avatars: new Map(),
  recentSolves: [],
  solvedAt: new Map(),
}
const listeners = new Set<() => void>()

function update(next: Partial<RosterState>) {
  state = { ...state, ...next }
  listeners.forEach((listener) => listener())
}

export const rosterStore = {
  getState: () => state,
  subscribe(listener: () => void) {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  },
  reset(connected: boolean) {
    update({ connected, myAvatarId: null, avatars: new Map() })
  },
  replaceAll(
    myAvatarId: string,
    avatars: AvatarState[],
    recentSolves: RecentSolve[],
  ) {
    update({
      myAvatarId,
      avatars: new Map(avatars.map((avatar) => [avatar.id, avatar])),
      recentSolves,
    })
  },
  recordSolve(avatarIds: string[], solve: RecentSolve, now: number) {
    const solvedAt = new Map(state.solvedAt)
    avatarIds.forEach((avatarId) => solvedAt.set(avatarId, now))
    update({
      solvedAt,
      recentSolves: [solve, ...state.recentSolves].slice(0, 5),
    })
  },
  put(avatar: AvatarState) {
    const avatars = new Map(state.avatars)
    avatars.set(avatar.id, avatar)
    update({ avatars })
  },
  remove(avatarId: string) {
    const avatars = new Map(state.avatars)
    avatars.delete(avatarId)
    update({ avatars })
  },
}

export function occupiedDeskIds(): Set<number> {
  const { avatars, myAvatarId } = state
  const desks = new Set<number>()
  for (const avatar of avatars.values()) {
    if (avatar.id !== myAvatarId && avatar.deskId !== null) {
      desks.add(avatar.deskId)
    }
  }
  return desks
}

export function useRecentSolves(): readonly RecentSolve[] {
  return useSyncExternalStore(rosterStore.subscribe, () => state.recentSolves)
}

export type SeatedNeighbor = Pick<
  AvatarState,
  'id' | 'nickname' | 'deskId' | 'activity'
> & { solvedAt: number | null }

let neighborsCache: { key: string; value: SeatedNeighbor[] } = {
  key: '',
  value: [],
}

function seatedNeighbors(): SeatedNeighbor[] {
  const value = [...state.avatars.values()]
    .filter(
      (avatar) => avatar.id !== state.myAvatarId && avatar.deskId !== null,
    )
    .map((avatar) => ({
      id: avatar.id,
      nickname: avatar.nickname,
      deskId: avatar.deskId,
      activity: avatar.activity,
      solvedAt: state.solvedAt.get(avatar.id) ?? null,
    }))
  const key = JSON.stringify(value)
  if (key !== neighborsCache.key) {
    neighborsCache = { key, value }
  }
  return neighborsCache.value
}

export function useSeatedNeighbors(): SeatedNeighbor[] {
  return useSyncExternalStore(rosterStore.subscribe, seatedNeighbors)
}

export function useOnlineCount(): number | null {
  return useSyncExternalStore(rosterStore.subscribe, () =>
    state.connected && state.myAvatarId !== null ? state.avatars.size : null,
  )
}
