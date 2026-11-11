import type { editor } from 'monaco-editor'
import { useEffect, useState } from 'react'
import { MonacoBinding } from 'y-monaco'
import { applyAwarenessUpdate, Awareness } from 'y-protocols/awareness'
import * as Y from 'yjs'
import type { DeskMessage } from '@/entities/desk/api/deskMessages'
import { loungeConnection } from '@/entities/lounge/api/loungeConnection'
import type { Language } from '@/shared/config/languages'
import { base64ToBytes } from '@/shared/lib/base64'

const REMOTE_ORIGIN = 'remote'

export type SpectateState =
  | { phase: 'connecting' }
  | {
      phase: 'watching'
      problemId: number
      language: Language
      nickname: string
      documentVersion: number
    }
  | { phase: 'closed' }
  | { phase: 'not-found' }

type Session = { doc: Y.Doc; awareness: Awareness }

export function useSpectate(roomId: string) {
  const [state, setState] = useState<SpectateState>({ phase: 'connecting' })
  const [codeEditor, setCodeEditor] =
    useState<editor.IStandaloneCodeEditor | null>(null)
  const [session, setSession] = useState<Session | null>(null)

  useEffect(() => {
    let current: Session | null = null

    function apply(kind: string, payload: string) {
      if (!current) return
      const bytes = base64ToBytes(payload)
      if (kind === 'doc') Y.applyUpdate(current.doc, bytes, REMOTE_ORIGIN)
      else applyAwarenessUpdate(current.awareness, bytes, REMOTE_ORIGIN)
    }

    const unsubscribeDesk = loungeConnection.onDeskMessage((frame) => {
      const message = JSON.parse(frame.body) as DeskMessage
      if (message.roomId !== roomId) return
      if (message.type === 'opened' || message.type === 'reset') {
        const doc = new Y.Doc()
        current = { doc, awareness: new Awareness(doc) }
        setSession(current)
        setState((previous) => ({
          phase: 'watching',
          problemId: message.problemId,
          language: message.language,
          nickname: message.nickname,
          documentVersion:
            previous.phase === 'watching' ? previous.documentVersion + 1 : 0,
        }))
      } else if (message.type === 'update' || message.type === 'full') {
        apply(message.kind, message.payload)
      } else if (message.type === 'closed') {
        setState({ phase: 'closed' })
      } else if (message.type === 'not-found') {
        setState({ phase: 'not-found' })
      }
    })
    const join = () => loungeConnection.send(`/app/desk/${roomId}/join`)
    const unsubscribeJoined = loungeConnection.onJoined(join)
    if (loungeConnection.isReady()) join()

    return () => {
      loungeConnection.send(`/app/desk/${roomId}/leave`)
      unsubscribeDesk()
      unsubscribeJoined()
      setSession(null)
    }
  }, [roomId])

  useEffect(() => {
    const model = codeEditor?.getModel()
    if (!codeEditor || !model || !session) return
    const binding = new MonacoBinding(
      session.doc.getText('code'),
      model,
      new Set([codeEditor]),
      session.awareness,
    )
    return () => binding.destroy()
  }, [codeEditor, session])

  useEffect(() => {
    if (!session) return
    return () => {
      session.awareness.destroy()
      session.doc.destroy()
    }
  }, [session])

  return { state, onEditorMount: setCodeEditor }
}
