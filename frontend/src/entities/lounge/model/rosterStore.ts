import { useSyncExternalStore } from 'react'
import type { AvatarState } from '@/entities/lounge/api/loungeMessages'

/**
 * 서버가 알려 준 "지금 라운지에 있는 모두"(나 포함). loungeConnection 이 쓰고, Phaser 장면과 React(접속자 수)가 읽는다.
 *
 * 다른 사람 좌표도 여기 들어 있어 초당 여러 번 바뀐다. 그래서 React 는 좌표가 아니라 접속자 수만 구독하고(useOnlineCount),
 * 좌표는 Phaser 가 매 프레임 직접 읽는다 (README 10장 "2단계").
 */
type RosterState = {
  connected: boolean
  myAvatarId: string | null
  avatars: ReadonlyMap<string, AvatarState>
}

let state: RosterState = {
  connected: false,
  myAvatarId: null,
  avatars: new Map(),
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
  replaceAll(myAvatarId: string, avatars: AvatarState[]) {
    update({
      myAvatarId,
      avatars: new Map(avatars.map((avatar) => [avatar.id, avatar])),
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

// 남이 앉아 있는 책상 번호들. 내 책상은 뺀다
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

// 서버에 연결되지 않았으면 null (상단 바에서 숫자를 숨긴다)
export function useOnlineCount(): number | null {
  return useSyncExternalStore(rosterStore.subscribe, () =>
    state.connected && state.myAvatarId !== null ? state.avatars.size : null,
  )
}
