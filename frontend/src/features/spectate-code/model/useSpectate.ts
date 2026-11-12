import type { editor } from 'monaco-editor'
import { useCallback, useEffect, useRef, useState } from 'react'
import { MonacoBinding } from 'y-monaco'
import {
  applyAwarenessUpdate,
  Awareness,
  encodeAwarenessUpdate,
} from 'y-protocols/awareness'
import * as Y from 'yjs'
import type {
  DeskMessage,
  PairChatMessage,
} from '@/entities/desk/api/deskMessages'
import { watchCursorLabels } from '@/entities/desk/lib/cursorLabels'
import { loungeConnection } from '@/entities/lounge/api/loungeConnection'
import { rosterStore } from '@/entities/lounge/model/rosterStore'
import type { Language } from '@/shared/config/languages'
import { base64ToBytes, bytesToBase64 } from '@/shared/lib/base64'

const REMOTE_ORIGIN = 'remote'

export type PairPhase = 'none' | 'requested' | 'declined' | 'paired'

export type SpectateState =
  | { phase: 'connecting' }
  | {
      phase: 'watching'
      problemId: number
      language: Language
      nickname: string
      pairable: boolean
      ownerAway: boolean
      documentVersion: number
    }
  | { phase: 'closed' }
  | { phase: 'not-found' }

type Session = { doc: Y.Doc; awareness: Awareness }

function myNickname() {
  const { avatars, myAvatarId } = rosterStore.getState()
  return (myAvatarId && avatars.get(myAvatarId)?.nickname) || '나'
}

export function useSpectate(routeRoomId: string) {
  const [target, setTarget] = useState(routeRoomId)
  const [roomId, setRoomId] = useState<string | null>(null)
  const [state, setState] = useState<SpectateState>({ phase: 'connecting' })
  const [codeEditor, setCodeEditor] =
    useState<editor.IStandaloneCodeEditor | null>(null)
  const [session, setSession] = useState<Session | null>(null)
  const [pair, setPair] = useState<PairPhase>('none')
  const [chats, setChats] = useState<PairChatMessage[]>([])
  const roomIdRef = useRef(routeRoomId)
  const pairedRef = useRef(false)

  if (routeRoomId !== target && routeRoomId !== roomId) {
    setTarget(routeRoomId)
    setRoomId(null)
    setState({ phase: 'connecting' })
  }

  useEffect(() => {
    pairedRef.current = pair === 'paired'
  }, [pair])

  useEffect(() => {
    let current: Session | null = null
    roomIdRef.current = target

    function apply(kind: string, payload: string) {
      if (!current) return
      const bytes = base64ToBytes(payload)
      if (kind === 'doc') Y.applyUpdate(current.doc, bytes, REMOTE_ORIGIN)
      else applyAwarenessUpdate(current.awareness, bytes, REMOTE_ORIGIN)
    }

    const unsubscribeDesk = loungeConnection.onDeskMessage((frame) => {
      const message = JSON.parse(frame.body) as DeskMessage
      const answersMyJoin =
        message.type === 'opened' && message.target === roomIdRef.current
      if (message.roomId !== roomIdRef.current && !answersMyJoin) return
      if (message.type === 'opened' || message.type === 'reset') {
        roomIdRef.current = message.roomId
        setRoomId(message.roomId)
        const doc = new Y.Doc()
        current = { doc, awareness: new Awareness(doc) }
        setSession(current)
        setState((previous) => ({
          phase: 'watching',
          problemId: message.problemId,
          language: message.language,
          nickname: message.nickname,
          pairable:
            message.pairable ??
            (previous.phase === 'watching' && previous.pairable),
          ownerAway: false,
          documentVersion:
            previous.phase === 'watching' ? previous.documentVersion + 1 : 0,
        }))
      } else if (message.type === 'update' || message.type === 'full') {
        apply(message.kind, message.payload)
      } else if (message.type === 'owner-away') {
        setState((previous) =>
          previous.phase === 'watching'
            ? { ...previous, ownerAway: true }
            : previous,
        )
      } else if (message.type === 'closed') {
        setState({ phase: 'closed' })
        setPair('none')
      } else if (message.type === 'not-found') {
        setState({ phase: 'not-found' })
      } else if (message.type === 'pair-started') {
        setPair('paired')
      } else if (message.type === 'pair-declined') {
        setPair('declined')
      } else if (message.type === 'pair-ended') {
        setPair('none')
        setChats([])
      } else if (message.type === 'chat') {
        setChats((previous) => [
          ...previous,
          {
            memberId: message.memberId,
            nickname: message.nickname,
            text: message.text,
            sentAt: message.sentAt,
          },
        ])
      }
    })
    const join = () => {
      setPair('none')
      loungeConnection.send(`/app/desk/${roomIdRef.current}/join`)
    }
    const unsubscribeJoined = loungeConnection.onJoined(join)
    if (loungeConnection.isReady()) join()

    return () => {
      loungeConnection.send(`/app/desk/${roomIdRef.current}/leave`)
      unsubscribeDesk()
      unsubscribeJoined()
      setSession(null)
    }
  }, [target])

  useEffect(() => {
    const model = codeEditor?.getModel()
    if (!codeEditor || !model || !session) return
    const { doc, awareness } = session
    awareness.setLocalStateField('user', { name: myNickname() })
    const binding = new MonacoBinding(
      doc.getText('code'),
      model,
      new Set([codeEditor]),
      awareness,
    )
    const stopLabels = watchCursorLabels(awareness)
    const send = (kind: 'doc' | 'awareness', bytes: Uint8Array) => {
      if (pairedRef.current) {
        loungeConnection.send(`/app/desk/${roomIdRef.current}/update`, {
          kind,
          payload: bytesToBase64(bytes),
        })
      }
    }
    const onDocUpdate = (update: Uint8Array, origin: unknown) => {
      if (origin !== REMOTE_ORIGIN) send('doc', update)
    }
    const onAwarenessUpdate = (
      {
        added,
        updated,
        removed,
      }: { added: number[]; updated: number[]; removed: number[] },
      origin: unknown,
    ) => {
      if (origin === REMOTE_ORIGIN) return
      send(
        'awareness',
        encodeAwarenessUpdate(awareness, [...added, ...updated, ...removed]),
      )
    }
    doc.on('update', onDocUpdate)
    awareness.on('update', onAwarenessUpdate)
    return () => {
      doc.off('update', onDocUpdate)
      awareness.off('update', onAwarenessUpdate)
      stopLabels()
      binding.destroy()
    }
  }, [codeEditor, session])

  useEffect(() => {
    if (!session) return
    return () => {
      session.awareness.destroy()
      session.doc.destroy()
    }
  }, [session])

  const requestPair = useCallback(() => {
    setPair('requested')
    loungeConnection.send(`/app/desk/${roomIdRef.current}/pair-request`)
  }, [])

  const endPair = useCallback(() => {
    loungeConnection.send(`/app/desk/${roomIdRef.current}/pair-end`)
  }, [])

  const sendChat = useCallback((text: string) => {
    loungeConnection.send(`/app/desk/${roomIdRef.current}/chat`, { text })
  }, [])

  return {
    roomId: roomId ?? target,
    state,
    pair,
    chats,
    onEditorMount: setCodeEditor,
    requestPair,
    endPair,
    sendChat,
  }
}
