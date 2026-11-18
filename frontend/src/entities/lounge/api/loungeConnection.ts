import { Client, type IMessage } from '@stomp/stompjs'
import {
  readGuestNickname,
  readSavedAppearance,
} from '@/entities/avatar/model/appearance'
import type {
  Direction,
  JoinRequest,
  LoungeEvent,
  MoveRequest,
  PersonalMessage,
} from '@/entities/lounge/api/loungeMessages'
import { activityTracker } from '@/entities/lounge/model/activityTracker'
import { loungeStore } from '@/entities/lounge/model/loungeStore'
import {
  occupiedDeskIds,
  rosterStore,
} from '@/entities/lounge/model/rosterStore'
import { readStorage, writeStorage } from '@/shared/lib/storage'

const MOVE_SEND_INTERVAL_MS = 100
const RECONNECT_DELAY_MS = 3000
const BROWSER_KEY_KEY = 'codingtest:v1:browserKey'
const CHAT_REJECTION_NOTICE = {
  empty: '보낼 내용을 적어 주세요.',
  'too-long': '200자까지 보낼 수 있어요.',
  'too-fast': '조금 천천히 보내 주세요.',
}

type Position = { x: number; y: number; direction: Direction }

let client: Client | null = null
let stopConnection = () => {}
const deskListeners = new Set<(message: IMessage) => void>()
const connectedListeners = new Set<() => void>()
let lastPosition: Position | null = null
let lastSentAt = 0
let lastSentMoving = false
let joinPending = false
let reportedDeskId: number | null = null

function readBrowserKey() {
  const saved = readStorage(localStorage, BROWSER_KEY_KEY)
  if (saved !== null) {
    return saved
  }
  const created = crypto.randomUUID()
  writeStorage(localStorage, BROWSER_KEY_KEY, created)
  return created
}

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
  if (lastPosition === null) {
    joinPending = true
    return
  }
  joinPending = false
  const { seatedDeskId } = loungeStore.getState()
  reportedDeskId = seatedDeskId
  const request: JoinRequest = {
    nickname: readGuestNickname(),
    coat: readSavedAppearance().coat,
    ...lastPosition,
    deskId: seatedDeskId,
    browserKey: readBrowserKey(),
  }
  publish('/app/lounge/join', request)
}

function publishActivity() {
  publish('/app/lounge/activity', { activity: activityTracker.get() })
}

function onEvent(message: IMessage) {
  const event = JSON.parse(message.body) as LoungeEvent
  if (event.type === 'chat') {
    rosterStore.addChat(event.chat, Date.now())
    return
  }
  if (event.type === 'solved') {
    rosterStore.recordSolve(
      event.avatarIds,
      event.solve,
      event.solvedTodayCount,
      Date.now(),
    )
    return
  }
  if (event.type === 'left') {
    rosterStore.remove(event.avatar.id)
    return
  }
  rosterStore.put(event.avatar)
  const mine = event.avatar.id === rosterStore.getState().myAvatarId
  if (mine && event.type === 'sat' && event.avatar.deskId !== null) {
    reportedDeskId = event.avatar.deskId
    loungeStore.sit(event.avatar.deskId)
  }
}

function onPersonal(message: IMessage) {
  const personal = JSON.parse(message.body) as PersonalMessage
  if (personal.type === 'snapshot') {
    rosterStore.replaceAll(
      personal.myAvatarId,
      personal.avatars,
      personal.recentSolves,
      personal.recentChats,
      personal.solvedTodayCount,
    )
    if (activityTracker.get() !== 'idle') {
      publishActivity()
    }
    const me = personal.avatars.find(
      (avatar) => avatar.id === personal.myAvatarId,
    )
    const { seatedDeskId } = loungeStore.getState()
    if (seatedDeskId !== null && me?.deskId === null) {
      moveToFreeDesk()
    }
    connectedListeners.forEach((listener) => listener())
    return
  }
  if (personal.type === 'replaced') {
    loungeConnection.yieldToOtherTab()
    return
  }
  if (personal.type === 'chat-rejected') {
    rosterStore.setChatNotice(CHAT_REJECTION_NOTICE[personal.reason])
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

const DESK_PREFERENCE = [
  14, 15, 8, 9, 20, 21, 13, 16, 7, 10, 19, 22, 2, 3, 26, 27, 1, 4, 25, 28, 12,
  17, 6, 11, 18, 23, 0, 5, 24, 29,
]

export function firstFreeDesk(): number | null {
  const occupied = occupiedDeskIds()
  return DESK_PREFERENCE.find((deskId) => !occupied.has(deskId)) ?? null
}

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
    if (client || rosterStore.getState().replaced) {
      return
    }
    const unsubscribeSeat = loungeStore.subscribe(syncSeatWithServer)
    const unsubscribeActivity = activityTracker.subscribe(publishActivity)
    const created: Client = new Client({
      brokerURL: webSocketUrl(),
      reconnectDelay: RECONNECT_DELAY_MS,
      onConnect: () => {
        if (client !== created) {
          return
        }
        rosterStore.reset(true)
        created.subscribe('/topic/lounge', onEvent)
        created.subscribe('/user/queue/lounge', onPersonal)
        created.subscribe('/user/queue/desk', (message) =>
          deskListeners.forEach((listener) => listener(message)),
        )
        join()
      },
      onWebSocketClose: () => {
        if (client === created) {
          rosterStore.reset(false)
        }
      },
    })
    client = created
    created.activate()
    stopConnection = () => {
      unsubscribeSeat()
      unsubscribeActivity()
      void created.deactivate()
    }
  },
  stop() {
    stopConnection()
    stopConnection = () => {}
    client = null
    rosterStore.reset(false)
  },
  restart() {
    loungeConnection.stop()
    rosterStore.setReplaced(false)
    loungeConnection.start()
  },
  yieldToOtherTab() {
    loungeConnection.stop()
    rosterStore.setReplaced(true)
  },
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
  isReady() {
    return (
      client?.connected === true && rosterStore.getState().myAvatarId !== null
    )
  },
  send(destination: string, body: object = {}) {
    publish(destination, body)
  },
  onDeskMessage(listener: (message: IMessage) => void) {
    deskListeners.add(listener)
    return () => {
      deskListeners.delete(listener)
    }
  },
  onJoined(listener: () => void) {
    connectedListeners.add(listener)
    return () => {
      connectedListeners.delete(listener)
    }
  },
  sendChat(text: string) {
    publish('/app/lounge/chat', { text })
  },
  requestSit(deskId: number) {
    if (client?.connected && rosterStore.getState().myAvatarId !== null) {
      publish('/app/lounge/sit', { deskId })
    } else {
      loungeStore.sit(deskId)
    }
  },
}
