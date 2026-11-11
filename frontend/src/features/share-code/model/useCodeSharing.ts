import type { editor } from 'monaco-editor'
import { useEffect, useState } from 'react'
import { MonacoBinding } from 'y-monaco'
import {
  applyAwarenessUpdate,
  Awareness,
  encodeAwarenessUpdate,
} from 'y-protocols/awareness'
import * as Y from 'yjs'
import type { DeskMessage } from '@/entities/desk/api/deskMessages'
import { loungeConnection } from '@/entities/lounge/api/loungeConnection'
import { rosterStore } from '@/entities/lounge/model/rosterStore'
import type { Language } from '@/shared/config/languages'
import { base64ToBytes, bytesToBase64 } from '@/shared/lib/base64'

const REMOTE_ORIGIN = 'remote'

export function useCodeSharing(
  codeEditor: editor.IStandaloneCodeEditor | null,
  problemId: number,
  language: Language,
) {
  const [spectatorCount, setSpectatorCount] = useState(0)

  useEffect(() => {
    const model = codeEditor?.getModel()
    if (!codeEditor || !model) {
      return
    }
    const doc = new Y.Doc()
    const text = doc.getText('code')
    text.insert(0, model.getValue())
    const awareness = new Awareness(doc)
    const binding = new MonacoBinding(
      text,
      model,
      new Set([codeEditor]),
      awareness,
    )
    let spectators = 0

    const roomId = () => rosterStore.getState().myAvatarId
    const host = () =>
      loungeConnection.send('/app/desk/host', { problemId, language })
    const sendUpdate = (kind: 'doc' | 'awareness', bytes: Uint8Array) => {
      const room = roomId()
      if (spectators > 0 && room !== null) {
        loungeConnection.send(`/app/desk/${room}/update`, {
          kind,
          payload: bytesToBase64(bytes),
        })
      }
    }

    const onDocUpdate = (update: Uint8Array, origin: unknown) => {
      if (origin !== REMOTE_ORIGIN) {
        sendUpdate('doc', update)
      }
    }
    const onAwarenessUpdate = ({
      added,
      updated,
      removed,
    }: {
      added: number[]
      updated: number[]
      removed: number[]
    }) => {
      sendUpdate(
        'awareness',
        encodeAwarenessUpdate(awareness, [...added, ...updated, ...removed]),
      )
    }
    doc.on('update', onDocUpdate)
    awareness.on('update', onAwarenessUpdate)

    const unsubscribeDesk = loungeConnection.onDeskMessage((frame) => {
      const message = JSON.parse(frame.body) as DeskMessage
      const room = roomId()
      if (message.roomId !== room || room === null) {
        return
      }
      if (message.type === 'spectators') {
        spectators = message.spectatorCount
        setSpectatorCount(message.spectatorCount)
      } else if (message.type === 'member-joined') {
        loungeConnection.send(`/app/desk/${room}/full`, {
          to: message.memberId,
          kind: 'doc',
          payload: bytesToBase64(Y.encodeStateAsUpdate(doc)),
        })
        loungeConnection.send(`/app/desk/${room}/full`, {
          to: message.memberId,
          kind: 'awareness',
          payload: bytesToBase64(
            encodeAwarenessUpdate(awareness, [doc.clientID]),
          ),
        })
      } else if (message.type === 'update' && message.kind === 'awareness') {
        applyAwarenessUpdate(
          awareness,
          base64ToBytes(message.payload),
          REMOTE_ORIGIN,
        )
      }
    })
    const unsubscribeJoined = loungeConnection.onJoined(() => {
      spectators = 0
      setSpectatorCount(0)
      host()
    })
    if (loungeConnection.isReady()) {
      host()
    }

    return () => {
      loungeConnection.send('/app/desk/close')
      unsubscribeDesk()
      unsubscribeJoined()
      doc.off('update', onDocUpdate)
      awareness.off('update', onAwarenessUpdate)
      binding.destroy()
      awareness.destroy()
      doc.destroy()
      setSpectatorCount(0)
    }
  }, [codeEditor, problemId, language])

  return spectatorCount
}
