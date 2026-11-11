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
