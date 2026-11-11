import { Client, type IMessage } from '@stomp/stompjs'
import {
  readGuestNickname,
  readSavedAppearance,
} from '@/entities/avatar/model/appearance'
import type {
  Direction,
  LoungeEvent,
  MoveRequest,
  PersonalMessage,
} from '@/entities/lounge/api/loungeMessages'
import { loungeStore } from '@/entities/lounge/model/loungeStore'
import {
  occupiedDeskIds,
  rosterStore,
} from '@/entities/lounge/model/rosterStore'

/**
 * 라운지 STOMP 연결 하나. React 와 Phaser 어느 쪽에도 속하지 않는 모듈이라, 화면이 바뀌어도 연결이 유지된다.
 *
 * 흐름: 연결 → 구독 2개(/topic/lounge 방송, /user/queue/lounge 나에게만) → 입장(join) → 나에게 snapshot, 남에게 joined
 * 끊기면 3초 뒤 자동으로 다시 연결하고, 지금 위치·책상으로 다시 입장한다. 로그인 후 새로고침도 같은 길로 회원이 되어 들어온다.
 */

// 위치는 움직일 때만 최대 100ms 에 한 번 보낸다 (docs/tuning.md "위치 전송 간격")
const MOVE_SEND_INTERVAL_MS = 100
const RECONNECT_DELAY_MS = 3000

type Position = { x: number; y: number; direction: Direction }

let client: Client | null = null
// start() 가 만든 연결과 구독을 정리하는 함수
let stopConnection = () => {}
let lastPosition: Position | null = null
let lastSentAt = 0
let lastSentMoving = false
let joinPending = false
// 서버에 마지막으로 알린 책상. 같은 앉기·일어나기를 두 번 보내지 않으려고 기억한다
let reportedDeskId: number | null = null

function webSocketUrl() {
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${scheme}://${window.location.host}/ws`
}

function publish(destination: string, body: object = {}) {
  if (client?.connected) {
    client.publish({ destination, body: JSON.stringify(body) })
  }
}

function join() {
  if (!client?.connected) {
    return
  }
  // 위치는 Phaser 장면이 처음 알려 줄 때까지 기다린다 (장면이 아직 안 떴을 수 있다)
  if (lastPosition === null) {
    joinPending = true
    return
  }
  joinPending = false
  const { seatedDeskId } = loungeStore.getState()
  reportedDeskId = seatedDeskId
  publish('/app/lounge/join', {
    nickname: readGuestNickname(),
    coat: readSavedAppearance().coat,
    ...lastPosition,
    deskId: seatedDeskId,
  })
}

function onEvent(message: IMessage) {
  const event = JSON.parse(message.body) as LoungeEvent
  if (event.type === 'left') {
    rosterStore.remove(event.avatar.id)
    return
  }
  rosterStore.put(event.avatar)
  const mine = event.avatar.id === rosterStore.getState().myAvatarId
  // F 로 앉기를 요청한 뒤 서버가 "앉았다"고 알려 주면 그때 실제로 앉는다
  if (mine && event.type === 'sat' && event.avatar.deskId !== null) {
    reportedDeskId = event.avatar.deskId
    loungeStore.sit(event.avatar.deskId)
  }
}

function onPersonal(message: IMessage) {
  const personal = JSON.parse(message.body) as PersonalMessage
  if (personal.type === 'snapshot') {
    rosterStore.replaceAll(personal.myAvatarId, personal.avatars)
    const me = personal.avatars.find(
      (avatar) => avatar.id === personal.myAvatarId,
    )
    // 앉은 채로 다시 연결했는데 그새 남이 그 책상에 앉았으면, 비어 있는 다른 책상으로 옮긴다
    const { seatedDeskId } = loungeStore.getState()
    if (seatedDeskId !== null && me?.deskId === null) {
      moveToFreeDesk()
    }
    return
  }
  if (personal.deskId === loungeStore.getState().seatedDeskId) {
    moveToFreeDesk()
  }
}

function moveToFreeDesk() {
  const free = firstFreeDesk()
  if (free === null) {
    loungeStore.stand()
  } else {
    reportedDeskId = null
    loungeStore.sit(free)
  }
}

// 문제 주소로 바로 들어오거나 자리를 뺏겼을 때 앉힐 책상. 가운데 줄 가운데(7번)부터 가까운 순서로 찾는다
const DESK_PREFERENCE = [7, 6, 8, 2, 12, 5, 9, 1, 3, 11, 13, 0, 4, 10, 14]

export function firstFreeDesk(): number | null {
  const occupied = occupiedDeskIds()
  return DESK_PREFERENCE.find((deskId) => !occupied.has(deskId)) ?? null
}

// 앉기·일어나기가 어디서 일어났든(F, ESC, 뒤로 가기, 자동 앉히기) 서버에 한 번만 알린다
function syncSeatWithServer() {
  const { seatedDeskId } = loungeStore.getState()
  if (seatedDeskId === reportedDeskId) {
    return
  }
  reportedDeskId = seatedDeskId
  if (seatedDeskId === null) {
    publish('/app/lounge/stand')
  } else {
    publish('/app/lounge/sit', { deskId: seatedDeskId })
  }
}

export const loungeConnection = {
  start() {
    if (client) {
      return
    }
    const unsubscribeSeat = loungeStore.subscribe(syncSeatWithServer)
    client = new Client({
      brokerURL: webSocketUrl(),
      reconnectDelay: RECONNECT_DELAY_MS,
      onConnect: () => {
        rosterStore.reset(true)
        client?.subscribe('/topic/lounge', onEvent)
        client?.subscribe('/user/queue/lounge', onPersonal)
        join()
      },
      // 끊기면 다른 사람 고양이를 지운다. 다시 연결되면 snapshot 으로 다시 채운다
      onWebSocketClose: () => rosterStore.reset(false),
    })
    const created = client
    created.activate()
    stopConnection = () => {
      unsubscribeSeat()
      void created.deactivate()
    }
  },
  stop() {
    stopConnection()
    stopConnection = () => {}
    client = null
    rosterStore.reset(false)
  },
  // 로그인·로그아웃으로 신원이 바뀌면 다시 연결해야 새 쿠키로 신원이 정해진다 (신원은 연결할 때 한 번만 정한다)
  restart() {
    loungeConnection.stop()
    loungeConnection.start()
  },
  /**
   * Phaser 장면이 매 프레임 부른다. 보내는 것은 움직이는 중 100ms 에 한 번, 그리고 멈춘 순간 한 번뿐이다.
   */
  reportPosition(position: Position, moving: boolean) {
    lastPosition = position
    if (joinPending) {
      join()
      return
    }
    const now = performance.now()
    const stopped = lastSentMoving && !moving
    if (!stopped && !(moving && now - lastSentAt >= MOVE_SEND_INTERVAL_MS)) {
      return
    }
    lastSentAt = now
    lastSentMoving = moving
    const request: MoveRequest = { ...position, moving }
    publish('/app/lounge/move', request)
  },
  /**
   * [F]로 앉기. 연결돼 있으면 서버에 묻고 "앉았다"는 방송을 받은 뒤에 앉는다(누가 먼저 앉았는지는 서버가 정한다).
   * 연결이 없으면(서버가 꺼져 있음) 혼자 앉는다.
   */
  requestSit(deskId: number) {
    if (client?.connected && rosterStore.getState().myAvatarId !== null) {
      publish('/app/lounge/sit', { deskId })
    } else {
      loungeStore.sit(deskId)
    }
  },
}
