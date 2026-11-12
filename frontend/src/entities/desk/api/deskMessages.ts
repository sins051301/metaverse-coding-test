import type { Language } from '@/shared/config/languages'

export type DeskPayloadKind = 'doc' | 'awareness'

export type DeskMessage =
  | {
      type: 'opened' | 'reset'
      roomId: string
      problemId: number
      language: Language
      nickname: string
    }
  | {
      type: 'update' | 'full'
      roomId: string
      kind: DeskPayloadKind
      payload: string
    }
  | { type: 'closed' | 'not-found'; roomId: string }
  | { type: 'member-joined'; roomId: string; memberId: string }
  | { type: 'spectators'; roomId: string; spectatorCount: number }
  | {
      type: 'pair-requested' | 'pair-started'
      roomId: string
      memberId: string
      nickname: string
    }
  | { type: 'pair-declined' | 'pair-ended'; roomId: string }
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
