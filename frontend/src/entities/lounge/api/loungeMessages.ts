import type { Coat } from '@/entities/avatar/model/appearance'

// 백엔드 com.codingtest.lounge.LoungeMessages 와 같은 모양이어야 한다

export type Direction = 'down' | 'up' | 'left' | 'right'

export type AvatarState = {
  id: string
  nickname: string
  member: boolean
  coat: Coat
  x: number
  y: number
  direction: Direction
  moving: boolean
  // 앉아 있으면 책상 번호, 서 있으면 null
  deskId: number | null
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

// /topic/lounge: 모두에게 방송
export type LoungeEvent = {
  type: 'joined' | 'moved' | 'sat' | 'stood' | 'left'
  avatar: AvatarState
}

// /user/queue/lounge: 나에게만
export type PersonalMessage =
  | { type: 'snapshot'; myAvatarId: string; avatars: AvatarState[] }
  | { type: 'sit-rejected'; deskId: number }
