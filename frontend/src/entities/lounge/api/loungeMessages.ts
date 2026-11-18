import type { Coat } from '@/entities/avatar/model/appearance'

export type Direction = 'down' | 'up' | 'left' | 'right'

export type Activity = 'idle' | 'typing' | 'napping'

export type AvatarState = {
  id: string
  nickname: string
  member: boolean
  coat: Coat
  x: number
  y: number
  direction: Direction
  moving: boolean
  deskId: number | null
  activity: Activity
}

export type RecentSolve = {
  nickname: string
  problemTitle: string
  solvedAt: string
}

export type ChatMessage = {
  avatarId: string
  nickname: string
  text: string
  sentAt: string
}

export type JoinRequest = {
  nickname: string
  coat: Coat
  x: number
  y: number
  direction: Direction
  deskId: number | null
  browserKey: string
}

export type MoveRequest = {
  x: number
  y: number
  direction: Direction
  moving: boolean
}

export type LoungeEvent =
  | {
      type: 'joined' | 'moved' | 'sat' | 'stood' | 'left' | 'activity'
      avatar: AvatarState
    }
  | {
      type: 'solved'
      avatarIds: string[]
      solve: RecentSolve
      solvedTodayCount: number
    }
  | { type: 'chat'; chat: ChatMessage }

export type PersonalMessage =
  | {
      type: 'snapshot'
      myAvatarId: string
      avatars: AvatarState[]
      recentSolves: RecentSolve[]
      recentChats: ChatMessage[]
      solvedTodayCount: number
    }
  | { type: 'sit-rejected'; deskId: number }
  | { type: 'chat-rejected'; reason: 'empty' | 'too-long' | 'too-fast' }
  | { type: 'replaced' }
