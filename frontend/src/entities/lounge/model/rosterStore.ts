import { useSyncExternalStore } from 'react'
import type {
  AvatarState,
  ChatMessage,
  RecentSolve,
} from '@/entities/lounge/api/loungeMessages'

const CHAT_HISTORY_LIMIT = 30

type RosterState = {
  connected: boolean
  myAvatarId: string | null
  avatars: ReadonlyMap<string, AvatarState>
  recentSolves: readonly RecentSolve[]
  solvedAt: ReadonlyMap<string, number>
  chats: readonly ChatMessage[]
  lastChatAt: ReadonlyMap<string, { text: string; at: number }>
  chatNotice: string | null
}

let state: RosterState = {
  connected: false,
  myAvatarId: null,
  avatars: new Map(),
  recentSolves: [],
  solvedAt: new Map(),
  chats: [],
  lastChatAt: new Map(),
  chatNotice: null,
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
    chats: ChatMessage[],
  ) {
    update({
      myAvatarId,
      avatars: new Map(avatars.map((avatar) => [avatar.id, avatar])),
      recentSolves,
      chats,
    })
  },
  addChat(chat: ChatMessage, now: number) {
    const lastChatAt = new Map(state.lastChatAt)
    lastChatAt.set(chat.avatarId, { text: chat.text, at: now })
    update({
      chats: [...state.chats, chat].slice(-CHAT_HISTORY_LIMIT),
      lastChatAt,
      chatNotice: null,
    })
  },
  setChatNotice(chatNotice: string | null) {
    update({ chatNotice })
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

export function useMyAvatarId(): string | null {
  return useSyncExternalStore(rosterStore.subscribe, () => state.myAvatarId)
}

export function useChats(): readonly ChatMessage[] {
  return useSyncExternalStore(rosterStore.subscribe, () => state.chats)
}

export function useChatNotice(): string | null {
  return useSyncExternalStore(rosterStore.subscribe, () => state.chatNotice)
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
