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

export type JoinRequest = {
  nickname: string
  coat: Coat
  x: number
  y: number
  direction: Direction
  deskId: number | null
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
  | { type: 'solved'; avatarIds: string[]; solve: RecentSolve }

export type PersonalMessage =
  | {
      type: 'snapshot'
      myAvatarId: string
      avatars: AvatarState[]
      recentSolves: RecentSolve[]
    }
  | { type: 'sit-rejected'; deskId: number }
