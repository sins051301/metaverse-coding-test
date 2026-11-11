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
import { activityTracker } from '@/entities/lounge/model/activityTracker'
import { loungeStore } from '@/entities/lounge/model/loungeStore'
import {
  occupiedDeskIds,
  rosterStore,
} from '@/entities/lounge/model/rosterStore'

const MOVE_SEND_INTERVAL_MS = 100
const RECONNECT_DELAY_MS = 3000

type Position = { x: number; y: number; direction: Direction }

let client: Client | null = null
let stopConnection = () => {}
let lastPosition: Position | null = null
let lastSentAt = 0
let lastSentMoving = false
let joinPending = false
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

function publishActivity() {
  publish('/app/lounge/activity', { activity: activityTracker.get() })
}

function onEvent(message: IMessage) {
  const event = JSON.parse(message.body) as LoungeEvent
  if (event.type === 'solved') {
    rosterStore.recordSolve(event.avatarIds, event.solve, Date.now())
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

const DESK_PREFERENCE = [7, 6, 8, 2, 12, 5, 9, 1, 3, 11, 13, 0, 4, 10, 14]

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
    if (client) {
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
    loungeConnection.start()
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
  requestSit(deskId: number) {
    if (client?.connected && rosterStore.getState().myAvatarId !== null) {
      publish('/app/lounge/sit', { deskId })
    } else {
      loungeStore.sit(deskId)
    }
  },
}
