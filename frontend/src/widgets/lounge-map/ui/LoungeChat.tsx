import { type FormEvent, useEffect, useRef, useState } from 'react'
import { loungeConnection } from '@/entities/lounge/api/loungeConnection'
import {
  useChatNotice,
  useChats,
  useMyAvatarId,
} from '@/entities/lounge/model/rosterStore'
import * as styles from './LoungeChat.css'

const CHAT_MAX_LENGTH = 200

type LoungeChatProps = {
  variant: 'overlay' | 'panel'
}

export function LoungeChat({ variant }: LoungeChatProps) {
  const chats = useChats()
  const notice = useChatNotice()
  const myAvatarId = useMyAvatarId()
  const [draft, setDraft] = useState('')
  const logRef = useRef<HTMLOListElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const sectionRef = useRef<HTMLElement>(null)

  useEffect(() => {
    logRef.current?.scrollTo({ top: logRef.current.scrollHeight })
  }, [chats])

  useEffect(() => {
    function onPointerDown(event: PointerEvent) {
      const input = inputRef.current
      if (
        input === document.activeElement &&
        !sectionRef.current?.contains(event.target as Node)
      ) {
        input?.blur()
      }
    }
    window.addEventListener('pointerdown', onPointerDown, true)
    return () => window.removeEventListener('pointerdown', onPointerDown, true)
  }, [])

  useEffect(() => {
    if (variant !== 'overlay') return
    function onKeyDown(event: KeyboardEvent) {
      const target = event.target
      const typing =
        target instanceof HTMLInputElement ||
        target instanceof HTMLTextAreaElement
      if (event.key === 'Enter' && !typing) {
        event.preventDefault()
        inputRef.current?.focus()
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [variant])

  function submit(event: FormEvent) {
    event.preventDefault()
    const text = draft.trim()
    if (text === '') return
    loungeConnection.sendChat(text)
    setDraft('')
  }

  return (
    <section
      ref={sectionRef}
      className={variant === 'overlay' ? styles.overlay : styles.panel}
      aria-label="라운지 채팅"
    >
      <ol ref={logRef} className={styles.log}>
        {chats.length === 0 ? (
          <li className={styles.empty}>라운지에 인사를 남겨 보세요.</li>
        ) : null}
        {chats.map((chat) => (
          <li key={`${chat.sentAt}-${chat.avatarId}`} className={styles.line}>
            <span
              className={
                chat.avatarId === myAvatarId ? styles.mine : styles.who
              }
            >
              {chat.avatarId === myAvatarId ? '나' : chat.nickname}
            </span>
            <span className={styles.text}>{chat.text}</span>
          </li>
        ))}
      </ol>
      {notice ? <p className={styles.notice}>{notice}</p> : null}
      <form className={styles.form} onSubmit={submit}>
        <input
          ref={inputRef}
          className={styles.input}
          value={draft}
          maxLength={CHAT_MAX_LENGTH}
          placeholder={
            variant === 'overlay'
              ? 'Enter 를 눌러 라운지에 말하기'
              : '라운지에 말하기'
          }
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Escape') event.currentTarget.blur()
          }}
        />
      </form>
    </section>
  )
}
