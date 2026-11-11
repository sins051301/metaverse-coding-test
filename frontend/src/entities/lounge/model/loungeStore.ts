import { useSyncExternalStore } from 'react'
import { readStorage, removeStorage, writeStorage } from '@/shared/lib/storage'

/**
 * React 와 Phaser 가 함께 보는 라운지 상태. 둘은 서로를 직접 부르지 않고 이 저장소만 읽고 쓴다.
 *
 * - Phaser(LoungeScene)가 쓰는 것: 근처 책상(nearbyDesk), 앉기(sit)
 * - React 가 쓰는 것: 화면 모드(view), 일어나기(stand), 지도 접힘(paused)
 * - 둘 다 subscribe 로 바뀐 것을 듣는다. React 는 useLoungeState(useSyncExternalStore)로 구독한다
 *
 * 고양이 좌표처럼 1초에 수십 번 바뀌는 값은 여기에 두지 않는다. 그런 값까지 React 가 구독하면 프레임마다 다시 그려야 한다.
 * 여기에는 "가끔" 바뀌는 값만 둔다 (README 10장 "라운지").
 */
export type NearbyDesk = {
  deskId: number
  // 지도 상자 왼쪽 위 기준 화면 좌표(px). [F] 툴팁을 이 위치에 띄운다
  screenX: number
  screenY: number
}

// lounge = 라운지 전체 화면에서 걸어 다님, panel = 책상에 앉아 왼쪽 패널로 작게 봄
export type LoungeView = 'lounge' | 'panel'

export type LoungeState = {
  view: LoungeView
  nearbyDesk: NearbyDesk | null
  seatedDeskId: number | null
  paused: boolean
}

// 새로고침해도 앉아 있던 책상에 그대로 앉아 있도록 탭 단위로 기억한다
const SEATED_DESK_KEY = 'codingtest:v1:seatedDesk'

function readSeatedDesk(): number | null {
  const saved = readStorage(sessionStorage, SEATED_DESK_KEY)
  return saved === null ? null : Number(saved)
}

let state: LoungeState = {
  view: 'lounge',
  nearbyDesk: null,
  seatedDeskId: readSeatedDesk(),
  paused: false,
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
  setPaused(paused: boolean) {
    if (paused !== state.paused) {
      update({ paused })
    }
  },
}

export function useLoungeState(): LoungeState {
  return useSyncExternalStore(loungeStore.subscribe, loungeStore.getState)
}
