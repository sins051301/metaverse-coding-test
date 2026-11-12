import type { editor } from 'monaco-editor'
import { useCallback, useEffect, useState } from 'react'
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

export type PairMember = { memberId: string; nickname: string }

function myNickname() {
  const { avatars, myAvatarId } = rosterStore.getState()
  return (myAvatarId && avatars.get(myAvatarId)?.nickname) || '주인'
}

export function useCodeSharing(
  codeEditor: editor.IStandaloneCodeEditor | null,
  problemId: number,
  language: Language,
) {
  const [spectatorCount, setSpectatorCount] = useState(0)
  const [pairRequests, setPairRequests] = useState<PairMember[]>([])
  const [partner, setPartner] = useState<PairMember | null>(null)
  const [chats, setChats] = useState<PairChatMessage[]>([])

  useEffect(() => {
    const model = codeEditor?.getModel()
    if (!codeEditor || !model) {
      return
    }
    const doc = new Y.Doc()
    const text = doc.getText('code')
    text.insert(0, model.getValue())
    const awareness = new Awareness(doc)
    awareness.setLocalStateField('user', { name: myNickname() })
    const binding = new MonacoBinding(
      text,
      model,
      new Set([codeEditor]),
      awareness,
    )
    const stopLabels = watchCursorLabels(awareness)
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
    const onAwarenessUpdate = (
      {
        added,
        updated,
        removed,
      }: { added: number[]; updated: number[]; removed: number[] },
      origin: unknown,
    ) => {
      if (origin === REMOTE_ORIGIN) return
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
      } else if (message.type === 'update') {
        const bytes = base64ToBytes(message.payload)
        if (message.kind === 'doc') Y.applyUpdate(doc, bytes, REMOTE_ORIGIN)
        else applyAwarenessUpdate(awareness, bytes, REMOTE_ORIGIN)
      } else if (message.type === 'pair-requested') {
        setPairRequests((previous) => [
          ...previous.filter(
            (request) => request.memberId !== message.memberId,
          ),
          { memberId: message.memberId, nickname: message.nickname },
        ])
      } else if (message.type === 'pair-started') {
        setPartner({ memberId: message.memberId, nickname: message.nickname })
        setPairRequests((previous) =>
          previous.filter((request) => request.memberId !== message.memberId),
        )
      } else if (message.type === 'pair-ended') {
        setPartner(null)
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
    const unsubscribeJoined = loungeConnection.onJoined(() => {
      spectators = 0
      setSpectatorCount(0)
      setPartner(null)
      setPairRequests([])
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
      stopLabels()
      binding.destroy()
      awareness.destroy()
      doc.destroy()
      setSpectatorCount(0)
      setPartner(null)
      setPairRequests([])
      setChats([])
    }
  }, [codeEditor, problemId, language])

  const answer = useCallback((memberId: string, accepted: boolean) => {
    loungeConnection.send(
      accepted ? '/app/desk/pair-accept' : '/app/desk/pair-decline',
      { memberId },
    )
    setPairRequests((previous) =>
      previous.filter((request) => request.memberId !== memberId),
    )
  }, [])

  const endPair = useCallback(() => {
    const room = rosterStore.getState().myAvatarId
    if (room !== null) loungeConnection.send(`/app/desk/${room}/pair-end`)
  }, [])

  const sendChat = useCallback((text: string) => {
    const room = rosterStore.getState().myAvatarId
    if (room !== null) loungeConnection.send(`/app/desk/${room}/chat`, { text })
  }, [])

  return {
    spectatorCount,
    pairRequests,
    partner,
    chats,
    answer,
    endPair,
    sendChat,
  }
}
