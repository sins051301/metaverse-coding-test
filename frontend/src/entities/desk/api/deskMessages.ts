import type { Language } from '@/shared/config/languages'

export type DeskPayloadKind = 'doc' | 'awareness'

export type DeskMessage =
  | { type: 'hosted'; roomId: string; pairable: boolean }
  | {
      type: 'opened' | 'reset'
      roomId: string
      target: string | null
      problemId: number
      language: Language
      nickname: string
      pairable: boolean | null
    }
  | {
      type: 'update' | 'full'
      roomId: string
      kind: DeskPayloadKind
      payload: string
    }
  | { type: 'closed' | 'not-found' | 'owner-away'; roomId: string }
  | { type: 'member-joined'; roomId: string; memberId: string }
  | { type: 'spectators'; roomId: string; spectatorCount: number }
  | {
      type: 'pair-requested' | 'pair-started'
      roomId: string
      memberId: string
      nickname: string
    }
  | { type: 'pair-declined' | 'pair-ended' | 'pair-away'; roomId: string }
  | {
      type: 'chat'
      roomId: string
      memberId: string
      nickname: string
      text: string
      sentAt: string
    }

export type PairChatMessage = {
  memberId: string
  nickname: string
  text: string
  sentAt: string
}
